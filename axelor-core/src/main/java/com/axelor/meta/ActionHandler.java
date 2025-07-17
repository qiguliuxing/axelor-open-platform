/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2005-2025 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.axelor.meta;

import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.db.QueryBinder;
import com.axelor.event.Event;
import com.axelor.event.NamedLiteral;
import com.axelor.events.PostAction;
import com.axelor.events.PreAction;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaFilter;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.schema.actions.ActionGroup;
import com.axelor.meta.schema.actions.ActionMethod;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;
import com.axelor.rpc.ContextEntity;
import com.axelor.rpc.Resource;
import com.axelor.script.CompositeScriptHelper;
import com.axelor.script.ScriptHelper;
import com.axelor.text.Templates;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableSet;
import com.google.common.escape.Escaper;
import com.google.common.escape.Escapers;
import com.google.common.io.CharStreams;
import com.google.inject.servlet.RequestScoped;
import jakarta.persistence.Query;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.script.Bindings;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.InvocationHandlerAdapter;
import net.bytebuddy.matcher.ElementMatchers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequestScoped
public class ActionHandler {

  private final Logger log = LoggerFactory.getLogger(ActionHandler.class);

  private final ActionRequest request;

  private final Event<PreAction> preActionEvent;

  private final Event<PostAction> postActionEvent;

  private final JpaSecurity security;

  private final Context context;

  private final Bindings bindings;

  private final ScriptHelper scriptHelper;

  private final Pattern pattern =
      Pattern.compile("^\\s*(select\\[\\]|select|action|call|eval):\\s*(.*)");

  private static final Set<Class<? extends Model>> ALWAYS_PERMITTED_MODELS =
      ImmutableSet.of(MetaAction.class, MetaFilter.class);

  ActionHandler(
      ActionRequest request,
      Event<PreAction> preActionEvent,
      Event<PostAction> postActionEvent,
      JpaSecurity security) {
    this.request = request;
    this.preActionEvent = preActionEvent;
    this.postActionEvent = postActionEvent;
    this.security = security;
    this.context = request.getContext();
    this.scriptHelper = new CompositeScriptHelper(this.context);
    this.bindings = this.scriptHelper.getBindings();
    this.bindings.put("__me__", this);
  }

  private Class<?> findClass(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    }
  }

  public Class<?> findModelClass(String model) {
    if (StringUtils.notBlank(model)) {
      return findClass(model);
    }
    if (context != null) {
      return context.getContextClass();
    }
    if (StringUtils.notBlank(request.getModel())) {
      return findClass(request.getModel());
    }
    return null;
  }

  public void checkPermission(JpaSecurity.AccessType accessType, String model) {
    final Class<?> checkClass = findModelClass(model);

    if (checkClass == null
        || !Model.class.isAssignableFrom(checkClass)
        || ALWAYS_PERMITTED_MODELS.contains(checkClass)) {
      return;
    }

    final Class<? extends Model> modelClass = checkClass.asSubclass(Model.class);

    if (context != null && context.getContextClass() == modelClass) {
      final Long id = (Long) context.get("id");
      if (id != null) {
        checkPermission(accessType, modelClass, id);
        return;
      }

      @SuppressWarnings("unchecked")
      final List<Object> idList = (List<Object>) context.get("_ids");
      if (ObjectUtils.notEmpty(idList)) {
        final Long[] ids =
            idList.stream().map(value -> Long.valueOf(String.valueOf(value))).toArray(Long[]::new);
        checkPermission(accessType, modelClass, ids);
        return;
      }
    }

    checkPermission(accessType, modelClass);
  }

  public void checkPermission(
      JpaSecurity.AccessType type, Class<? extends Model> model, Long... ids) {
    security.check(type, model, ids);
  }

  public void firePreEvent(String name) {
    preActionEvent.select(NamedLiteral.of(name)).fire(new PreAction(name, context));
  }

  public PostAction firePostEvent(String name, Object result) {
    PostAction event = new PostAction(name, context, result);
    postActionEvent.select(NamedLiteral.of(name)).fire(event);
    return event;
  }

  public Context getContext() {
    return context;
  }

  public ActionRequest getRequest() {
    return request;
  }

  /**
   * Evaluate the given <code>expression</code>.
   *
   * @param expression the expression to evaluate prefixed with action type followed by a <code>:
   *     </code>
   * @return expression result
   */
  public Object evaluate(String expression) {

    if (StringUtils.isEmpty(expression)) {
      return null;
    }

    String expr = expression.trim();
    if (expr.startsWith("#{") && expr.endsWith("}")) {
      return handleScript(expr);
    }

    String kind = null;
    Matcher matcher = pattern.matcher(expression);

    if (matcher.matches()) {
      kind = matcher.group(1);
      expr = matcher.group(2);
    } else {
      return expr;
    }

    if ("eval".equals(kind)) {
      return handleScript(expr);
    }

    if ("action".equals(kind)) {
      return handleAction(expr);
    }

    if ("call".equals(kind)) {
      return handleCall(expr);
    }

    if ("select".equals(kind)) {
      return handleSelectOne(expr);
    }

    if ("select[]".equals(kind)) {
      return handleSelectAll(expr);
    }

    return expr;
  }

  public Object call(String className, String method) {
    ActionResponse response = new ActionResponse();
    try {
      final Class<?> klass = Class.forName(className);
      final Method m = klass.getMethod(method, ActionRequest.class, ActionResponse.class);
      final Object obj = Beans.get(klass);
      m.invoke(obj, new Object[] {request, response});
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      response.setException(e);
    }
    return response;
  }

  public Object rpc(String className, String methodCall) {

    Pattern pattern = Pattern.compile("(\\w+)\\((.*?)\\)");
    Matcher matcher = pattern.matcher(methodCall);

    if (!matcher.matches()) {
      return null;
    }

    String methodName = matcher.group(1);
    String methodArgs = matcher.group(2);

    try {
      final Class<?> klass = Class.forName(className);
      final List<Method> methods =
          Arrays.stream(klass.getMethods())
              .filter(m -> m.getName().equals(methodName))
              .collect(Collectors.toList());

      // method not found
      if (methods.size() == 0) {
        throw new IllegalArgumentException(
            new NoSuchMethodException("%s.%s()".formatted(className, methodName)));
      }

      // validate no-args or only matched method
      if (methods.size() == 1 || StringUtils.isBlank(methodArgs)) {
        Method method = methods.getFirst();
        if (method.getAnnotation(CallMethod.class) == null) {
          throw new IllegalArgumentException(
              "Action not allowed: %s:%s".formatted(className, methodCall));
        }
      } else { // validate exact matched method with arguments
        final Object validator =
            new ByteBuddy()
                .subclass(klass)
                .method(ElementMatchers.named(methodName))
                .intercept(
                    InvocationHandlerAdapter.of(
                        (proxy, method, args) -> {
                          if (method.getAnnotation(CallMethod.class) == null) {
                            throw new IllegalArgumentException(
                                "Action not allowed: %s:%s".formatted(className, methodCall));
                          }
                          return null;
                        }))
                .make()
                .load(klass.getClassLoader())
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance();

        // validate method
        scriptHelper.call(validator, methodCall);
      }

      final Object object = Beans.get(klass);
      return scriptHelper.call(object, methodCall);
    } catch (Exception e) {
      throw new IllegalArgumentException(e);
    }
  }

  public String template(Templates engine, Reader template) throws IOException {
    return engine.fromText(CharStreams.toString(template)).make(bindings).render();
  }

  @SuppressWarnings("all")
  private Query select(String query, Object... params) {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(query));
    if (!query.toLowerCase().startsWith("select ")) query = "SELECT " + query;

    Query q = JPA.em().createQuery(query);
    QueryBinder.of(q).bind(bindings, params);

    return q;
  }

  public Object selectOne(String query, Object... params) {
    Query q = select(query, params);
    q.setMaxResults(1);
    try {
      return q.getResultList().getFirst();
    } catch (Exception e) {
    }
    return null;
  }

  public Object selectAll(String query, Object... params) {
    try {
      return select(query, params).getResultList();
    } catch (Exception e) {
    }
    return null;
  }

  public Object selectOne(String query) {
    return selectOne(query, new Object[] {});
  }

  public Object selectAll(String query) {
    return selectAll(query, new Object[] {});
  }

  @SuppressWarnings("all")
  public Object search(Class<?> entityClass, String filter, Map params) {
    filter =
        makeMethodCall("__repo__(%s).all().filter".formatted(entityClass.getSimpleName()), filter);
    com.axelor.db.Query q = (com.axelor.db.Query) handleScript(filter);

    q = q.bind(bindings);
    q = q.bind(params);

    return q.fetchOne();
  }

  private static final Escaper STRING_ESCAPER = Escapers.builder().addEscape('"', "\\\"").build();

  private String makeMethodCall(String method, String expression) {
    expression = expression.trim();
    // check if expression is parameterized
    if (!expression.startsWith("(")) {
      if (!expression.matches("('|\")")) {
        expression = "\"" + STRING_ESCAPER.escape(expression) + "\"";
      }
      expression = "(" + expression + ")";
    }
    return "#{" + method + expression + "}";
  }

  private Object handleSelectOne(String expression) {
    expression = makeMethodCall("__me__.selectOne", expression);
    return handleScript(expression);
  }

  private Object handleSelectAll(String expression) {
    expression = makeMethodCall("__me__.selectAll", expression);
    return handleScript(expression);
  }

  private Object handleScript(String expression) {
    return scriptHelper.eval(expression);
  }

  private Object handleAction(String expression) {

    Action action = MetaStore.getAction(expression);
    if (action == null) {
      log.debug("no such action found: {}", expression);
      return null;
    }

    return action.execute(this);
  }

  private Object handleCall(String expression) {

    if (Strings.isNullOrEmpty(expression)) return null;

    String[] parts = expression.split("\\:", 2);
    if (parts.length != 2) {
      log.error("Invalid call expression: ", expression);
      return null;
    }

    ActionMethod action = new ActionMethod();
    ActionMethod.Call call = new ActionMethod.Call();

    call.setController(parts[0]);
    call.setMethod(parts[1]);
    action.setCall(call);
    action.setName(expression);

    return action.execute(this);
  }

  private static final String KEY_VALUES = "values";
  private static final String KEY_ATTRS = "attrs";
  private static final String KEY_VALUE = "value";

  private Object toCompact(final Object item) {
    if (item == null) return null;
    if (item instanceof Collection) {
      return Collections2.transform(
          (Collection<?>) item,
          new Function<Object, Object>() {
            @Override
            public Object apply(Object input) {
              return toCompact(input);
            }
          });
    }
    if (item instanceof Model) {
      Model bean = (Model) item;
      if (bean.getId() != null && JPA.em().contains(bean)) {
        return Resource.toMapCompact(bean);
      }
    }
    return item;
  }

  /**
   * This method finds m2o values which are managed instances and converts them to compact maps to
   * avoid unnecessary data transmission and prevents object graph recreation issues.
   */
  @SuppressWarnings("all")
  private Object process(Object data) {
    if (data == null || data instanceof ContextEntity) return data;
    if (data instanceof Collection) {
      final List items = new ArrayList<>();
      for (Object item : (Collection) data) {
        items.add(process(item));
      }
      return items;
    }
    if (data instanceof Map) {
      final Map<String, Object> item = new HashMap<>((Map<String, Object>) data);
      if (item.containsKey(KEY_VALUES)
          && item.get(KEY_VALUES) instanceof Map
          && !(item.get(KEY_VALUES) instanceof ContextEntity)) {
        final Map<String, Object> values = (Map) item.get(KEY_VALUES);
        for (String key : values.keySet()) {
          Object value = values.get(key);
          if (value instanceof Model) {
            values.put(key, toCompact(value));
          }
        }
      }
      if (item.containsKey(KEY_ATTRS)
          && item.get(KEY_ATTRS) instanceof Map
          && !(item.get(KEY_ATTRS) instanceof ContextEntity)) {
        final Map<String, Object> values = (Map) item.get(KEY_ATTRS);
        for (String key : values.keySet()) {
          final Map<String, Object> attrs = (Map) values.get(key);
          if (attrs.containsKey(KEY_VALUE)) {
            attrs.put(KEY_VALUE, toCompact(attrs.get(KEY_VALUE)));
          }
        }
      }
      return item;
    }
    return data;
  }

  public ActionResponse execute() {

    ActionResponse response = new ActionResponse();

    String name = request.getAction();
    if (name == null) {
      throw new NullPointerException("no action provided");
    }

    String[] names = name.split(",");
    ActionGroup action = new ActionGroup();

    for (String item : names) {
      action.addAction(item);
    }

    Object data = action.wrap(this);

    if (data instanceof ActionResponse) {
      return (ActionResponse) data;
    }

    response.setData(process(data));
    response.setStatus(ActionResponse.STATUS_SUCCESS);

    return response;
  }
}
