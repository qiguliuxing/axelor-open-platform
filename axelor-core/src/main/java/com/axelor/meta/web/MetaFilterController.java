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
package com.axelor.meta.web;

import com.axelor.i18n.I18n;
import com.axelor.meta.db.MetaFilter;
import com.axelor.meta.service.MetaFilterService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.ResponseException;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;

public class MetaFilterController {

  @Inject private MetaFilterService service;

  public void saveFilter(ActionRequest request, ActionResponse response) {
    MetaFilter ctx = request.getContext().asType(MetaFilter.class);
    if (ctx != null) {
      try {
        ctx = service.saveFilter(ctx);
        response.setData(ctx);
      } catch (PersistenceException e) {
        response.setException(
            new ResponseException(
                I18n.get("Please provide a different name for the filter."),
                I18n.get("Filter name already used"),
                null));
      }
    }
  }

  public void removeFilter(ActionRequest request, ActionResponse response) {
    MetaFilter ctx = request.getContext().asType(MetaFilter.class);
    if (ctx != null) {
      ctx = service.removeFilter(ctx);
      response.setData(ctx);
    }
  }

  public void findFilters(ActionRequest request, ActionResponse response) {
    MetaFilter ctx = request.getContext().asType(MetaFilter.class);
    if (ctx != null && ctx.getFilterView() != null) {
      response.setData(service.getFilters(ctx.getFilterView()));
    }
  }
}
