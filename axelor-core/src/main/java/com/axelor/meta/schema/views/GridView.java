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

import com.axelor.common.StringUtils;
import com.axelor.rpc.Request;
import com.axelor.script.ScriptHelper;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import jakarta.xml.bind.annotation.XmlAnyAttribute;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import jakarta.xml.bind.annotation.XmlElements;
import jakarta.xml.bind.annotation.XmlTransient;
import jakarta.xml.bind.annotation.XmlType;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.namespace.QName;

@XmlType
@JsonTypeName("grid")
public class GridView extends AbstractView implements ContainerView, ExtendableView {

  @XmlAttribute private Boolean sortable;

  @XmlAttribute private String orderBy;

  @XmlAttribute private String groupBy;

  @XmlAttribute private Boolean customSearch;

  @XmlAttribute private String freeSearch;

  @XmlAttribute private String onNew;

  @XmlAttribute private String onSave;

  @XmlAttribute private String onDelete;

  @XmlAttribute private Boolean canNew;

  @XmlAttribute private Boolean canEdit;

  @XmlAttribute private Boolean canSave;

  @XmlAttribute private Boolean canDelete;

  @XmlAttribute private Boolean canArchive;

  @XmlAttribute private Boolean canMove;

  @XmlAttribute(name = "summary-view")
  private String summaryView;

  @XmlAttribute(name = "edit-icon")
  private Boolean editIcon = Boolean.TRUE;

  @XmlAttribute(name = "x-row-height")
  private Integer rowHeight;

  @XmlAttribute(name = "x-col-width")
  private Integer colWidth;

  @XmlAttribute(name = "x-no-fetch")
  private Boolean noFetch;

  @XmlAttribute(name = "x-selector")
  private String selector;

  @XmlAttribute(name = "x-tree-field")
  private String treeField;

  @XmlAttribute(name = "x-tree-field-title")
  private String treeFieldTitle;

  @XmlAttribute(name = "x-tree-limit")
  private Integer treeLimit;

  @JsonIgnore @XmlAnyAttribute private Map<QName, String> otherAttributes;

  @XmlElement(name = "help")
  private Help inlineHelp;

  @XmlAttribute private String widget;

  @XmlElementWrapper
  @XmlElement(name = "button")
  private List<Button> toolbar;

  @XmlElementWrapper
  @XmlElement(name = "menu")
  private List<Menu> menubar;

  @XmlElement(name = "hilite")
  private List<Hilite> hilites;

  @XmlElements({
    @XmlElement(name = "field", type = PanelField.class),
    @XmlElement(name = "button", type = Button.class)
  })
  private List<AbstractWidget> items;

  @XmlElement(name = "extend")
  private List<Extend> extendItems;

  public Boolean getSortable() {
    return sortable;
  }

  public void setSortable(Boolean sortable) {
    this.sortable = sortable;
  }

  public String getOrderBy() {
    return orderBy;
  }

  public void setOrderBy(String orderBy) {
    this.orderBy = orderBy;
  }

  public String getGroupBy() {
    return groupBy;
  }

  public void setGroupBy(String groupBy) {
    this.groupBy = groupBy;
  }

  public Boolean getCustomSearch() {
    return customSearch;
  }

  public void setCustomSearch(Boolean customSearch) {
    this.customSearch = customSearch;
  }

  public String getFreeSearch() {
    return freeSearch;
  }

  public void setFreeSearch(String freeSearch) {
    this.freeSearch = freeSearch;
  }

  public String getSummaryView() {
    return summaryView;
  }

  public void setSummaryView(String summaryView) {
    this.summaryView = summaryView;
  }

  public String getOnNew() {
    return onNew;
  }

  public void setOnNew(String onNew) {
    this.onNew = onNew;
  }

  public String getOnSave() {
    return onSave;
  }

  public void setOnSave(String onSave) {
    this.onSave = onSave;
  }

  public String getOnDelete() {
    return onDelete;
  }

  public void setOnDelete(String onDelete) {
    this.onDelete = onDelete;
  }

  public Boolean getCanNew() {
    return canNew;
  }

  public void setCanNew(Boolean canNew) {
    this.canNew = canNew;
  }

  public Boolean getCanEdit() {
    return canEdit;
  }

  public void setCanEdit(Boolean canEdit) {
    this.canEdit = canEdit;
  }

  public Boolean getCanSave() {
    return canSave;
  }

  public void setCanSave(Boolean canSave) {
    this.canSave = canSave;
  }

  public Boolean getCanDelete() {
    return canDelete;
  }

  public void setCanDelete(Boolean canDelete) {
    this.canDelete = canDelete;
  }

  public Boolean getCanArchive() {
    return canArchive;
  }

  public void setCanArchive(Boolean canArchive) {
    this.canArchive = canArchive;
  }

  public Boolean getCanMove() {
    return canMove;
  }

  public void setCanMove(Boolean canMove) {
    this.canMove = canMove;
  }

  public Boolean getEditIcon() {
    return editIcon;
  }

  public void setEditIcon(Boolean editIcon) {
    this.editIcon = editIcon;
  }

  public Integer getRowHeight() {
    return rowHeight;
  }

  public void setRowHeight(Integer rowHeight) {
    this.rowHeight = rowHeight;
  }

  public Integer getColWidth() {
    return colWidth;
  }

  public void setColWidth(Integer colWidth) {
    this.colWidth = colWidth;
  }

  public Boolean getNoFetch() {
    return noFetch;
  }

  public void setNoFetch(Boolean noFetch) {
    this.noFetch = noFetch;
  }

  public String getSelector() {
    return selector;
  }

  public void setSelector(String selector) {
    this.selector = selector;
  }

  public String getTreeField() {
    return treeField;
  }

  public void setTreeField(String treeField) {
    this.treeField = treeField;
  }

  public String getTreeFieldTitle() {
    return treeFieldTitle;
  }

  public void setTreeFieldTitle(String treeFieldTitle) {
    this.treeFieldTitle = treeFieldTitle;
  }

  public Integer getTreeLimit() {
    return treeLimit;
  }

  public void setTreeLimit(Integer treeLimit) {
    this.treeLimit = treeLimit;
  }

  public Map<QName, String> getOtherAttributes() {
    return otherAttributes;
  }

  public void setOtherAttributes(Map<QName, String> otherAttributes) {
    this.otherAttributes = otherAttributes;
  }

  @XmlTransient
  public Map<String, Object> getWidgetAttrs() {
    return AbstractWidget.getWidgetAttrs(otherAttributes);
  }

  @XmlTransient
  public void setWidgetAttrs(Map<String, Object> attrs) {
    // does nothing
  }

  public Help getInlineHelp() {
    if (inlineHelp == null) {
      return null;
    }

    if (isBlank(inlineHelp.getConditionToCheck())) {
      return inlineHelp;
    }

    final String condition = inlineHelp.getConditionToCheck();
    final Request request = Request.current();
    if (request == null) {
      return inlineHelp;
    }

    final ScriptHelper helper = request.getScriptHelper();
    return helper.test(condition) ? inlineHelp : null;
  }

  public void setInlineHelp(Help inlineHelp) {
    this.inlineHelp = inlineHelp;
  }

  public String getWidget() {
    return widget;
  }

  public void setWidget(String widget) {
    this.widget = widget;
  }

  public List<Hilite> getHilites() {
    return hilites;
  }

  public void setHilites(List<Hilite> hilites) {
    this.hilites = hilites;
  }

  public List<Button> getToolbar() {
    if (toolbar != null) {
      for (Button button : toolbar) {
        button.setModel(this.getModel());
      }
    }
    return toolbar;
  }

  public void setToolbar(List<Button> toolbar) {
    this.toolbar = toolbar;
  }

  public List<Menu> getMenubar() {
    if (menubar != null) {
      for (Menu menu : menubar) {
        menu.setModel(this.getModel());
      }
    }
    return menubar;
  }

  public void setMenubar(List<Menu> menubar) {
    this.menubar = menubar;
  }

  @Override
  public List<AbstractWidget> getItems() {
    if (items != null) {
      for (AbstractWidget field : items) {
        field.setModel(super.getModel());
      }
    }
    return items;
  }

  public void setItems(List<AbstractWidget> items) {
    this.items = items;
  }

  @Override
  public Set<String> getExtraNames() {
    return Stream.of(getOrderBy(), getTreeField())
        .filter(n -> !StringUtils.isBlank(n))
        .collect(Collectors.toSet());
  }

  @Override
  public List<Extend> getExtends() {
    return extendItems;
  }

  @Override
  public void setExtends(List<Extend> extendItems) {
    this.extendItems = extendItems;
  }
}
