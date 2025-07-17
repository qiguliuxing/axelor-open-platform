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

import static com.axelor.common.StringUtils.isBlank;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;

@XmlType
@JsonTypeName("field")
public class PanelField extends Field {

  @XmlElement private PanelViewer viewer;

  @XmlElement private PanelEditor editor;

  @XmlTransient @JsonIgnore private boolean fromEditor;

  public PanelViewer getViewer() {
    if (viewer != null) {
      viewer.forField = this;
    }
    return viewer;
  }

  public void setViewer(PanelViewer viewer) {
    this.viewer = viewer;
  }

  public PanelEditor getEditor() {
    if (editor != null) {
      editor.forField = this;
      editor.setModel(isBlank(getTarget()) ? getModel() : getTarget());
    }
    return editor;
  }

  public void setEditor(PanelEditor editor) {
    this.editor = editor;
  }

  public boolean isFromEditor() {
    return fromEditor;
  }

  void setFromEditor(boolean fromEditor) {
    this.fromEditor = fromEditor;
  }
}
