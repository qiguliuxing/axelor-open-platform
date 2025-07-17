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
package com.axelor.meta.schema.actions;

import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JpaSecurity;
import com.axelor.events.PostAction;
import com.axelor.meta.ActionHandler;
import com.axelor.rpc.ActionResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.MoreObjects;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@XmlType(name = "AbstractAction")
public abstract class Action {

  protected final transient Logger log = LoggerFactory.getLogger(getClass());

  @XmlAttribute(name = "id")
  private String xmlId;

  @XmlTransient @JsonProperty private Long actionId;

  @XmlAttribute private String name;

  @XmlAttribute private String model;

  @XmlAttribute(name = "if-module")
  private String moduleToCheck;

  public String getXmlId() {
    return xmlId;
  }

  public void setXmlId(String xmlId) {
    this.xmlId = xmlId;
  }

  public Long getActionId() {
    return actionId;
  }

  public void setActionId(Long actionId) {
    this.actionId = actionId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public String getModuleToCheck() {
    return moduleToCheck;
  }

  public void setModuleToCheck(String moduleToCheck) {
    this.moduleToCheck = moduleToCheck;
  }

  public Object execute(ActionHandler handler) {
    final Object result;

    if (StringUtils.isBlank(getName())) {
      result = evaluate(handler);
    } else {
      checkPermission(handler);
      handler.firePreEvent(getName());
      final Object value = evaluate(handler);
      PostAction event = handler.firePostEvent(getName(), value);
      result = event.getResult();
    }

    return result;
  }

  protected void checkPermission(ActionHandler handler) {
    handler.checkPermission(JpaSecurity.CAN_READ, getModel());
  }

  public Object wrap(ActionHandler handler) {
    Object result = execute(handler);
    return result instanceof ActionResponse ? result : wrapper(result);
  }

  protected Object wrapper(Object value) {
    return value;
  }

  protected abstract Object evaluate(ActionHandler handler);

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass()).add("name", getName()).toString();
  }

  protected static String toExpression(String expression, boolean quote) {
    Pattern pattern = Pattern.compile("^(#\\{|(eval|select|action):)");
    if (expression != null && !pattern.matcher(expression).find()) {
      expression = "eval: " + (quote ? "\"\"\"" + expression + "\"\"\"" : expression);
    }
    return expression;
  }

  protected static boolean test(ActionHandler handler, String expression) {
    // if expression is not given always return true
    if (StringUtils.isBlank(expression)) return true;

    if ("true".equals(expression)) return true;
    if ("false".equals(expression)) return false;

    Object result = handler.evaluate(toExpression(expression, false));
    if (result instanceof Boolean booleanResult) return booleanResult;
    if (result instanceof Number numberResult)
      return Double.compare(numberResult.doubleValue(), 0) != 0;

    return ObjectUtils.notEmpty(result);
  }

  @XmlType
  public abstract static class Element {

    @XmlAttribute(name = "if")
    private String condition;

    @XmlAttribute private String name;

    @XmlAttribute(name = "expr")
    private String expression;

    public String getCondition() {
      return condition;
    }

    public void setCondition(String condition) {
      this.condition = condition;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getExpression() {
      return expression;
    }

    public void setExpression(String expression) {
      this.expression = expression;
    }

    public boolean test(ActionHandler handler) {
      return Action.test(handler, getCondition());
    }
  }
}
