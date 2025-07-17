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
package com.axelor.data.xml;

import com.axelor.common.StringUtils;
import com.axelor.db.JpaRepository;
import com.axelor.db.mapper.JsonProperty;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaJsonRecord;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@XStreamAlias("bind")
public class XMLBindJson extends XMLBind {

  @XStreamAlias("json-model")
  @XStreamAsAttribute
  private String jsonModel;

  private String domain;

  private XMLBind parent;

  private List<XMLBind> jsonBindings;

  private boolean initialized;

  private void initialize() {
    try {
      if (StringUtils.notBlank(jsonModel)) {
        setTypeName(MetaJsonRecord.class.getName());
        domain = "self.jsonModel = :jsonModel";
        return;
      }

      if (parent == null) {
        return;
      }

      final Optional<String> parentJsonModel =
          Optional.of(parent)
              .filter(XMLBindJson.class::isInstance)
              .map(XMLBindJson.class::cast)
              .map(XMLBindJson::getJsonModel)
              .filter(StringUtils::notBlank);
      final List<String> fieldParts =
          Arrays.asList(
              getField().substring(JsonProperty.KEY_JSON_PREFIX.length()).split("\\.", 2));

      @SuppressWarnings("unchecked")
      final Map<String, Object> jsonField =
          (Map<String, Object>)
              Optional.ofNullable(
                      parentJsonModel.isPresent()
                          ? MetaStore.findJsonFields(parentJsonModel.get())
                          : MetaStore.findJsonFields(parent.getTypeName(), fieldParts.getFirst()))
                  .map(fields -> fields.get(fieldParts.get(1)))
                  .orElse(Collections.emptyMap());
      jsonModel = (String) jsonField.get("jsonTarget");
      setTypeName((String) jsonField.get("target"));
      domain = (String) jsonField.get("domain");
    } finally {
      initialized = true;
    }
  }

  public String getJsonModel() {
    if (!initialized) {
      initialize();
    }

    return jsonModel;
  }

  public void setJsonModel(String jsonModel) {
    this.jsonModel = jsonModel;
    initialized = false;
  }

  @Override
  public String getTypeName() {
    if (!initialized) {
      initialize();
    }

    return super.getTypeName();
  }

  @Override
  public Class<?> getType() {
    if (!initialized) {
      initialize();
    }

    return super.getType();
  }

  public XMLBind getParent() {
    return parent;
  }

  public void setParent(XMLBind parent) {
    this.parent = parent;
  }

  @Override
  public String getSearch() {
    final String search = super.getSearch();

    if (StringUtils.isBlank(getJsonModel()) || StringUtils.isBlank(search)) {
      return search;
    }

    return Optional.of(
            Stream.of(search, domain)
                .filter(StringUtils::notBlank)
                .map(item -> "(%s)".formatted(item))
                .collect(Collectors.joining(" AND ")))
        .filter(StringUtils::notBlank)
        .orElse(null);
  }

  @Override
  public List<XMLBind> getBindings() {
    if (StringUtils.notBlank(getJsonModel())) {
      final List<XMLBind> bindings =
          Optional.ofNullable(super.getBindings()).orElse(Collections.emptyList());
      return Stream.concat(getJsonBindings().stream(), bindings.stream())
          .collect(Collectors.toList());
    }

    return super.getBindings();
  }

  private List<XMLBind> getJsonBindings() {
    if (jsonBindings == null) {
      final XMLBind jsonModelBinding = new XMLBind();
      jsonModelBinding.setField("jsonModel");
      jsonModelBinding.setExpression("'%s'".formatted(getJsonModel()));
      jsonBindings = Collections.singletonList(jsonModelBinding);
    }

    return jsonBindings;
  }

  @Override
  public Object postProcess(Object bean) {
    if (StringUtils.notBlank(getJsonModel())) {
      return JpaRepository.of(MetaJsonRecord.class).save((MetaJsonRecord) bean);
    }

    return super.postProcess(bean);
  }
}
