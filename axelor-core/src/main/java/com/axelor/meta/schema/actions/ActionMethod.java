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
