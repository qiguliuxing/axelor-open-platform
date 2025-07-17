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
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
@XmlTransient
public abstract class AbstractContainer extends SimpleWidget {

  @XmlAttribute private Integer cols;

  @XmlAttribute private String colWidths;

  @XmlAttribute private String gap;

  @XmlAttribute private Integer itemSpan;

  public Integer getCols() {
    return cols;
  }

  public void setCols(Integer cols) {
    this.cols = cols;
  }

  public String getColWidths() {
    return colWidths;
  }

  public void setColWidths(String colWidths) {
    this.colWidths = colWidths;
  }

  public String getGap() {
    return gap;
  }

  public void setGap(String gap) {
    this.gap = gap;
  }

  public Integer getItemSpan() {
    return itemSpan;
  }

  public void setItemSpan(Integer itemSpan) {
    this.itemSpan = itemSpan;
  }
}
