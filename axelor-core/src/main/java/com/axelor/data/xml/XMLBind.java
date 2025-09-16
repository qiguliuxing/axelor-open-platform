/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.data.xml;

import com.axelor.data.DataScriptHelper;
import com.axelor.data.ImportException;
import com.axelor.inject.Beans;
import com.google.common.base.Strings;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import com.thoughtworks.xstream.annotations.XStreamImplicit;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.runtime.InvokerHelper;

@XStreamAlias("bind")
public class XMLBind {

  @XStreamAsAttribute private String node;

  @XStreamAlias("to")
  @XStreamAsAttribute
  private String field;

  @XStreamAsAttribute private String alias;

  @XStreamAlias("type")
  @XStreamAsAttribute
  private String typeName;

  @XStreamAsAttribute private String search;

  @XStreamAsAttribute private Boolean update;

  @XStreamAsAttribute private Boolean create;

  @XStreamAlias("eval")
  @XStreamAsAttribute
  private String expression;

  @XStreamAlias("if")
  @XStreamAsAttribute
  private String condition;

  @XStreamAlias("if-empty")
  @XStreamAsAttribute
  private Boolean conditionEmpty;

  @XStreamAlias("call")
  @XStreamAsAttribute
  private String callable;

  @XStreamAsAttribute private String adapter;

  @XStreamImplicit(itemFieldName = "bind")
  private List<XMLBind> bindings;

  private boolean bindingsLinked;

  public String getNode() {
    return node;
  }

  public void setNode(String node) {
    this.node = node;
  }

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getAlias() {
    return alias;
  }

  public void setAlias(String alias) {
    this.alias = alias;
  }

  public String getAliasOrName() {
    if (alias == null || "".equals(alias.trim())) return node;
    return alias;
  }

  public String getTypeName() {
    return typeName;
  }

  public void setTypeName(String typeName) {
    this.typeName = typeName;
  }

  public Class<?> getType() {
    try {
      return Class.forName(typeName);
    } catch (ClassNotFoundException e) {
    }
    return null;
  }

  public String getSearch() {
    return search;
  }

  public void setSearch(String search) {
    this.search = search;
  }

  public Boolean getUpdate() {
    return update;
  }

  public void setUpdate(Boolean update) {
    this.update = update;
  }

  public Boolean getCreate() {
    return create;
  }

  public void setCreate(Boolean create) {
    this.create = create;
  }

  public String getExpression() {
    return expression;
  }

  public void setExpression(String expression) {
    this.expression = expression;
  }

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public Boolean getConditionEmpty() {
    return conditionEmpty;
  }

  public void setConditionEmpty(Boolean conditionEmpty) {
    this.conditionEmpty = conditionEmpty;
  }

  public String getCallable() {
    return callable;
  }

  public void setCallable(String callable) {
    this.callable = callable;
  }

  public String getAdapter() {
    return adapter;
  }

  public void setAdapter(String adapter) {
    this.adapter = adapter;
  }

  public List<XMLBind> getBindings() {
    if (!bindingsLinked) {
      linkBindings();
    }

    return bindings;
  }

  public void setBindings(List<XMLBind> bindings) {
    this.bindings = bindings;
  }

  private void linkBindings() {
    if (bindings == null) {
      return;
    }

    bindings.stream()
        .filter(XMLBindJson.class::isInstance)
        .map(XMLBindJson.class::cast)
        .forEach(binding -> binding.setParent(this));
    bindingsLinked = true;
  }

  private Set<String> multiples;

  public boolean isMultiple(XMLBind bind) {
    if (multiples == null) {
      multiples = new HashSet<>();
      Set<String> found = new HashSet<>();
      for (XMLBind b : bindings) {
        if (found.contains(b.getNode())) {
          multiples.add(b.getNode());
        }
        found.add(b.getNode());
      }
    }
    return multiples.contains(bind.getNode());
  }

  private Object callObject;
  private Method callMethod;

  @SuppressWarnings("unchecked")
  public <T> T call(T object, Map<String, Object> context) throws Exception {

    if (Strings.isNullOrEmpty(callable)) return object;

    if (callObject == null) {

      String className = callable.split("\\:")[0];
      String method = callable.split("\\:")[1];

      Class<?> klass = Class.forName(className);

      callMethod = klass.getMethod(method, Object.class, Map.class);
      callObject = Beans.get(klass);
    }

    try {
      return (T) callMethod.invoke(callObject, new Object[] {object, context});
    } catch (Exception e) {
      System.err.println("EEE: " + e);
      throw new ImportException(e);
    }
  }

  public static Pattern pattern = Pattern.compile("^(call|eval):\\s*(.*)");
  private static DataScriptHelper helper = new DataScriptHelper(100, 10, false);

  public Object evaluate(Map<String, Object> context) {
    if (Strings.isNullOrEmpty(expression)) {
      return handleGroovy(context);
    }

    String kind = null;
    String expr = expression;
    Matcher matcher = pattern.matcher(expression);

    if (matcher.matches()) {
      kind = matcher.group(1);
      expr = matcher.group(2);
    } else {
      return handleGroovy(context);
    }

    if ("call".equals(kind)) {
      return handleCall(context, expr);
    }

    return handleGroovy(context);
  }

  private Object handleGroovy(Map<String, Object> context) {
    if (Strings.isNullOrEmpty(expression)) {
      return context.get(this.getAliasOrName());
    }
    return helper.eval(expression, context);
  }

  public boolean validate(Map<String, Object> context) {
    if (Strings.isNullOrEmpty(condition)) {
      return true;
    }
    String expr = condition + " ? true : false";
    return (Boolean) helper.eval(expr, context);
  }

  private Object handleCall(Map<String, Object> context, String expr) {
    if (Strings.isNullOrEmpty(expr)) {
      return null;
    }

    try {

      String className = expr.split("\\:")[0];
      String method = expr.split("\\:")[1];

      Class<?> klass = Class.forName(className);
      Object object = Beans.get(klass);

      Pattern p = Pattern.compile("(\\w+)\\((.*?)\\)");
      Matcher m = p.matcher(method);

      if (!m.matches()) return null;

      String methodName = m.group(1);
      String params = "[" + m.group(2) + "] as Object[]";
      Object[] arguments = (Object[]) helper.eval(params, context);

      return InvokerHelper.invokeMethod(object, methodName, arguments);
    } catch (Exception e) {
      System.err.println("EEE: " + e);
      return null;
    }
  }

  public Object postProcess(Object bean) {
    return bean;
  }

  @Override
  public String toString() {

    StringBuilder sb = new StringBuilder("<bind");

    if (node != null) sb.append(" node='").append(node).append("'");
    if (field != null) sb.append(" to=='").append(field).append("'");
    if (typeName != null) sb.append(" type='").append(typeName).append("'");
    if (alias != null) sb.append(" alias='").append(alias).append("'");

    return sb.append(" ... >").toString();
  }
}
