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

import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.List;

@XmlType
public abstract class AbstractPanel extends AbstractContainer {

  @XmlAttribute private Boolean showFrame;

  @XmlAttribute private Boolean sidebar;

  @XmlAttribute private Boolean stacked;

  @XmlAttribute private Boolean attached;

  @XmlAttribute private String onTabSelect;

  public Boolean getShowFrame() {
    return showFrame;
  }

  public void setShowFrame(Boolean showFrame) {
    this.showFrame = showFrame;
  }

  public Boolean getSidebar() {
    return sidebar;
  }

  public void setSidebar(Boolean sidebar) {
    this.sidebar = sidebar;
  }

  public Boolean getStacked() {
    return stacked;
  }

  public void setStacked(Boolean stacked) {
    this.stacked = stacked;
  }

  public Boolean getAttached() {
    return attached;
  }

  public void setAttached(Boolean attached) {
    this.attached = attached;
  }

  public String getOnTabSelect() {
    return onTabSelect;
  }

  public void setOnTabSelect(String onTabSelect) {
    this.onTabSelect = onTabSelect;
  }

  protected List<AbstractWidget> process(List<AbstractWidget> items) {
    if (items == null) {
      items = new ArrayList<>();
    }
    for (AbstractWidget item : items) {
      item.setModel(getModel());
    }
    return items;
  }
}
