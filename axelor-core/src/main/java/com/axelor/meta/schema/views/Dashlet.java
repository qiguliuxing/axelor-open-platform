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
package com.axelor.meta.schema.views;

import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
@JsonTypeName("dashlet")
public class Dashlet extends AbstractPanel {

  @XmlAttribute private String action;

  @XmlAttribute private Boolean canSearch;

  @XmlAttribute private String canNew;

  @XmlAttribute private String canEdit;

  @XmlAttribute private String canDelete;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public Boolean getCanSearch() {
    return canSearch;
  }

  public void setCanSearch(Boolean canSearch) {
    this.canSearch = canSearch;
  }

  public String getCanNew() {
    return canNew;
  }

  public void setCanNew(String canNew) {
    this.canNew = canNew;
  }

  public String getCanEdit() {
    return canEdit;
  }

  public void setCanEdit(String canEdit) {
    this.canEdit = canEdit;
  }

  public String getCanDelete() {
    return canDelete;
  }

  public void setCanDelete(String canDelete) {
    this.canDelete = canDelete;
  }
}
