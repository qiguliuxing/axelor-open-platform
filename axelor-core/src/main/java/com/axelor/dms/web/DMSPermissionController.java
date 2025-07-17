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
package com.axelor.dms.web;

import com.axelor.db.Model;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.DMSPermission;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public class DMSPermissionController {

  public void setUserDomain(ActionRequest request, ActionResponse response) {
    response.setAttr("user", "domain", getDomain(request, DMSPermission::getUser));
  }

  public void setGroupDomain(ActionRequest request, ActionResponse response) {
    response.setAttr("group", "domain", getDomain(request, DMSPermission::getGroup));
  }

  /**
   * Gets domain string in order to suggest unique users/groups in a DMS file's permissions.
   *
   * @param request
   * @param getter
   * @return
   */
  private String getDomain(ActionRequest request, Function<DMSPermission, Model> getter) {
    final DMSPermission permission = request.getContext().asType(DMSPermission.class);
    final DMSFile file = request.getContext().getParent().asType(DMSFile.class);
    final Model current = getter.apply(permission);
    final String idListString =
        Optional.ofNullable(file.getPermissions()).orElse(Collections.emptyList()).stream()
            .map(getter)
            .filter(Objects::nonNull)
            .filter(model -> current == null || !current.equals(model))
            .map(Model::getId)
            .map(Number::toString)
            .collect(Collectors.joining(","));
    return "self.id NOT IN (%s)".formatted(idListString.isEmpty() ? "0" : idListString);
  }
}
