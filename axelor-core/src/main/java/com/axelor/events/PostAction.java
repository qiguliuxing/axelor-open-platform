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
package com.axelor.events;

import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Context;

public class PostAction extends ActionEvent {

  private Object result;

  public PostAction(String name, Context context, Object result) {
    super(name, context);
    this.result = result;
  }

  public Object getResult() {
    return result;
  }

  public void setResult(Object result) {
    if (this.result instanceof ActionResponse
        && result != null
        && !(result instanceof ActionResponse)) {
      String msg = "Expected action result of type '%s' but was '%s'";
      throw new IllegalArgumentException(
          msg.formatted(ActionResponse.class.getName(), result.getClass().getName()));
    }

    this.result = result;
  }
}
