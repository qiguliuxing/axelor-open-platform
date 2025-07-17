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

import java.util.List;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
public class Extend {

  @XmlAttribute private String target;

  @XmlAttribute(name = "if-feature")
  private String featureToCheck;

  @XmlAttribute(name = "if-module")
  private String moduleToCheck;

  @XmlElement(name = "insert")
  private List<ExtendItemInsert> inserts;

  @XmlElement(name = "replace")
  private List<ExtendItemReplace> replaces;

  @XmlElement(name = "move")
  private List<ExtendItemMove> moves;

  @XmlElement(name = "attribute")
  private List<ExtendItemAttribute> attributes;

  public String getTarget() {
    return target;
  }

  public void setTarget(String target) {
    this.target = target;
  }

  public String getFeatureToCheck() {
    return featureToCheck;
  }

  public void setFeatureToCheck(String featureToCheck) {
    this.featureToCheck = featureToCheck;
  }

  public String getModuleToCheck() {
    return moduleToCheck;
  }

  public void setModuleToCheck(String moduleToCheck) {
    this.moduleToCheck = moduleToCheck;
  }

  public List<ExtendItemInsert> getInserts() {
    return inserts;
  }

  public void setInserts(List<ExtendItemInsert> inserts) {
    this.inserts = inserts;
  }

  public List<ExtendItemReplace> getReplaces() {
    return replaces;
  }

  public void setReplaces(List<ExtendItemReplace> replaces) {
    this.replaces = replaces;
  }

  public List<ExtendItemMove> getMoves() {
    return moves;
  }

  public void setMoves(List<ExtendItemMove> moves) {
    this.moves = moves;
  }

  public List<ExtendItemAttribute> getAttributes() {
    return attributes;
  }

  public void setAttributes(List<ExtendItemAttribute> attributes) {
    this.attributes = attributes;
  }
}
