/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.meta.schema.actions;

import com.axelor.meta.ActionHandler;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import java.util.regex.Pattern;

@XmlType
public class ActionMethod extends Action {

  @XmlType
  public static class Call extends Element {

    @XmlAttribute private String method;

    @XmlAttribute(name = "class")
    private String controller;

    public String getMethod() {
      return method;
    }

    public void setMethod(String method) {
      this.method = method;
    }

    public String getController() {
      return controller;
    }

    public void setController(String controller) {
      this.controller = controller;
    }
  }

  @XmlElement(name = "call")
  private Call call;

  public Call getCall() {
    return call;
  }

  public void setCall(Call call) {
    this.call = call;
  }

  private boolean isRpc(String methodCall) {
    return Pattern.matches("(\\w+)\\((.*?)\\)", methodCall);
  }

  @Override
  protected Object evaluate(ActionHandler handler) {
    if (!call.test(handler)) {
      log.debug("action '{}' doesn't meet the condition: {}", getName(), call.getCondition());
      return null;
    }
    if (isRpc(call.getMethod())) return handler.rpc(call.getController(), call.getMethod());
    return handler.call(call.getController(), call.getMethod());
  }
}
