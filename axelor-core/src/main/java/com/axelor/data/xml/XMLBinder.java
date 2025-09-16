/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.data.xml;

import com.axelor.common.XMLUtils;
import com.axelor.data.AuditHelper;
import com.axelor.data.adapter.DataAdapter;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import org.apache.commons.jxpath.JXPathContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public abstract class XMLBinder {

  private static final Logger LOG = LoggerFactory.getLogger(XMLBinder.class);

  private XMLInput input;

  private Map<String, Object> context;

  private boolean newBean;

  private Map<String, DataAdapter> adapters = new HashMap<>();

  protected XMLBinder(XMLInput input, Map<String, Object> context) {
    this.input = input;
    this.context = context;
  }

  public void registerAdapter(DataAdapter adapter) {
    adapters.put(adapter.getName(), adapter);
  }

  protected abstract void handle(Object bean, XMLBind bind, Map<String, Object> context);

  protected abstract void finish();

  private Class<?> lastClass = null;

  public void bind(Document element) {
    for (XMLBind binding : input.getBindings()) {
      LOG.debug("binding: " + binding);
      List<Node> nodes = this.find(element, binding, "/");
      for (Node node : nodes) {
        if (lastClass != binding.getType()) {
          lastClass = binding.getType();
          JPA.flush();
        }
        LOG.trace("element: <{} ...>", node.getNodeName());
        Map<String, Object> map = this.toMap(node, binding);
        Object bean = this.bind(binding, binding.getType(), map);
        LOG.trace("bean created: {}", bean);
        this.handle(bean, binding, toContext(map));
        LOG.trace("bean saved: {}", bean);
      }
    }
  }

  @SuppressWarnings("unchecked")
  private Object bind(XMLBind binding, Class<?> type, Map<String, Object> values) {

    if (type == null || values == null || values.size() == 0) {
      return null;
    }

    Object bean = null;
    Map<String, Object> ctx = toContext(values);

    LOG.trace("context: " + ctx);

    if (binding.getSearch() != null) {
      LOG.trace("search: " + binding.getSearch());
      bean = JPA.all((Class<Model>) type).filter(binding.getSearch()).bind(ctx).fetchOne();
      LOG.trace("search found: " + bean);
      if (bean != null && !Boolean.TRUE.equals(binding.getUpdate())) {
        LOG.trace("search no update");
        return bean;
      } else if (bean == null && Boolean.FALSE.equals(binding.getCreate())) {
        LOG.trace("search no create");
        return null;
      }
    }

    Mapper mapper = Mapper.of(type);
    List<XMLBind> bindings = binding.getBindings();
    boolean isNull = bean == null;
    newBean = isNull;

    if (bindings == null) {
      return bean;
    }

    if (isNull) {
      bean = newInstance(type);
    }

    LOG.trace("populate: " + type);

    for (final XMLBind bind : bindings) {

      LOG.trace("binding: " + bind);

      final String field = bind.getField();
      final String name = bind.getAlias() != null ? bind.getAlias() : field;
      final Property property = mapper.getProperty(bean, field);

      if (property == null) { // handle dummy binding
        // TODO: this.handleDummyBind(bind, values);
        continue;
      }

      if (property.isPrimary() || property.isVirtual()) {
        continue;
      }

      Object value = values.get(name);

      LOG.trace("value: " + value);
      LOG.trace("condition: " + bind.getCondition());

      if (newBean == false
          && Boolean.TRUE.equals(bind.getConditionEmpty())
          && property.get(bean) != null) {
        LOG.trace("field is not empty");
        continue;
      }

      if (!validate(bind, value, ctx)) {
        LOG.trace("condition failed");
        continue;
      }

      // process eval expression
      if (bind.getExpression() != null) {
        LOG.trace("expression: " + bind.getExpression());
        // default value is already computed so only do eval for node binding
        value = bind.getNode() == null ? value : bind.evaluate(ctx);
        LOG.trace("value: " + value);
      }

      if (value instanceof Model) {
        // do nothing
      } else if (property.isReference()) {
        value = relational(property, bind, value, ctx);
      } else if (property.isCollection() && value != null) {
        if (!(value instanceof List)) {
          value = List.of(value);
        }
        List<Object> items = new ArrayList<>();
        for (Object item : (List<?>) value) {
          items.add(relational(property, bind, item, ctx));
        }
        value = items;
      }

      LOG.trace("set value: {} = {}", property.getName(), value);
      isNull = false;

      if (property.isCollection()) {
        if (value == null) {
        } else if (value instanceof Collection<?> collection) {
          property.addAll(bean, collection);
        } else {
          property.add(bean, value);
        }
      } else if (!AuditHelper.update(bean, field, value)) {
        property.set(bean, value);
      }
    }

    return isNull ? null : binding.postProcess(bean);
  }

  @SuppressWarnings("all")
  private Object relational(
      Property property, XMLBind bind, Object value, Map<String, Object> ctx) {

    Map<String, Object> values = ctx;
    if (value instanceof Map map) {
      values = map;
      // copy underscored context variables
      for (String key : ctx.keySet()) {
        if (key.startsWith("_")) {
          values.put(key, ctx.get(key));
        }
      }
    }

    Object result = bind(bind, property.getTarget(), values);

    if (result instanceof Model model
        && (property.getType() == PropertyType.MANY_TO_ONE
            || property.getType() == PropertyType.MANY_TO_MANY)) {
      if (!JPA.em().contains(result)) {
        result = JPA.manage(model);
      }
    }
    return result;
  }

  private Map<String, Object> toContext(Map<String, Object> map) {
    Map<String, Object> ctx = new HashMap<>();
    if (context != null) {
      ctx.putAll(context);
    }
    if (map != null) {
      ctx.putAll(map);
    }
    return ctx;
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private boolean validate(XMLBind binding, Object value, Map<String, Object> values) {
    Map<String, Object> ctx = toContext(value instanceof Map m ? m : values);
    if (values != null) {
      // copy underscored context variables
      for (String key : values.keySet()) {
        if (key.startsWith("_")) {
          ctx.put(key, values.get(key));
        }
      }
    }
    return binding.validate(ctx);
  }

  @SuppressWarnings("all")
  private Map<String, Object> toMap(Node node, XMLBind binding) {

    Map<String, Object> map = new HashMap<>();

    // first prepare complete map
    for (XMLBind bind : binding.getBindings()) {
      String name = bind.getAlias();
      String path = bind.getNode();
      if (name == null) {
        name = bind.getField();
      }

      if (name == null) {
        continue;
      }

      List<Node> nodes = find(node, bind, ".");
      Object value = value(nodes, bind);

      value = this.adapt(bind, value, map);

      if (!validate(bind, value, map)) {
        continue;
      }

      // get default value
      if (bind.getNode() == null && bind.getExpression() != null) {
        value = bind.evaluate(toContext(map));
      }

      map.put(name, value);
    }
    return map;
  }

  private Object value(List<Node> nodes, final XMLBind bind) {
    List<Object> result =
        nodes.stream()
            .map(
                input -> {
                  if (bind.getBindings() != null) {
                    return toMap(input, bind);
                  }
                  if (input.getNodeType() == Node.ELEMENT_NODE) {
                    Node child = input.getFirstChild();
                    if (child == null) {
                      return null;
                    }
                    if (child.getNodeType() == Node.TEXT_NODE) {
                      return child.getNodeValue();
                    }
                    return toMap(input, bind);
                  }
                  return input.getNodeValue();
                })
            .collect(Collectors.toList());
    if (result.size() == 1) {
      return result.getFirst();
    }
    return result.size() == 0 ? null : result;
  }

  private Object adapt(XMLBind bind, Object value, Map<String, Object> ctx) {
    String name = bind.getAdapter();
    if (name == null || !adapters.containsKey(name)) {
      return value;
    }
    DataAdapter adapter = adapters.get(name);
    return adapter.adapt(value, ctx);
  }

  private XPath xpath = null;

  @SuppressWarnings("unchecked")
  private List<Node> selectNodes(Node node, String path) throws XPathExpressionException {
    // if referencing parents use jdk xpath, see RM-6911
    if (path.contains("../")) {
      if (xpath == null) {
        xpath = XMLUtils.createXPath();
      }
      final XPathExpression expression = xpath.compile(path);
      final List<Node> nodes = new ArrayList<>();
      final NodeList items = (NodeList) expression.evaluate(node, XPathConstants.NODESET);
      for (int i = 0; i < items.getLength(); i++) {
        nodes.add(items.item(i));
      }
      return nodes;
    }
    return JXPathContext.newContext(node).selectNodes(path);
  }

  private List<Node> find(Node node, XMLBind bind, String prefix) {
    List<Node> nodes = new ArrayList<>();
    String name = bind.getNode();
    String path = name;

    if (name == null) {
      return nodes;
    }

    if (!path.startsWith("/")) {
      path = "/" + path;
    }
    if (!("/".equals(prefix))) {
      path = prefix + path;
    }

    try {
      LOG.trace("xpath: " + path);
      nodes = selectNodes(node, path);
      LOG.trace("xpath match: " + nodes.size());
    } catch (Exception e) {
      LOG.error("Invalid xpath expression: {}", path);
    }
    return nodes;
  }

  private Object newInstance(Class<?> type) {
    try {
      return type.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }
}
