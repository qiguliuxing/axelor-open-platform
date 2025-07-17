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

import com.axelor.common.StringUtils;
import com.axelor.i18n.I18n;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
@JsonTypeName("button")
public class Button extends SimpleWidget {

  @XmlAttribute private String icon;

  @XmlAttribute private String iconHover;

  @XmlAttribute private String link;

  @XmlAttribute private String prompt;

  @XmlAttribute private String onClick;

  @XmlAttribute private String widget;

  @JsonGetter("title")
  public String getLocalizedTitle() {
    String title = getTitle();
    if (StringUtils.isBlank(title)) {
      return null;
    }
    return I18n.get(title);
  }

  public String getIcon() {
    return icon;
  }

  public void setIcon(String icon) {
    this.icon = icon;
  }

  public String getIconHover() {
    return iconHover;
  }

  public void setIconHover(String iconHover) {
    this.iconHover = iconHover;
  }

  public String getLink() {
    return link;
  }

  public void setLink(String link) {
    this.link = link;
  }

  @JsonGetter("prompt")
  public String getLocalizedPrompt() {
    return I18n.get(prompt);
  }

  @JsonIgnore
  public String getPrompt() {
    return prompt;
  }

  @JsonSetter
  public void setPrompt(String prompt) {
    this.prompt = prompt;
  }

  public String getOnClick() {
    return onClick;
  }

  public void setOnClick(String onClick) {
    this.onClick = onClick;
  }

  public String getWidget() {
    return widget;
  }

  public void setWidget(String widget) {
    this.widget = widget;
  }
}
