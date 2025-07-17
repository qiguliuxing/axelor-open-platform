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
package com.axelor.data.csv;

import com.axelor.common.StringUtils;
import com.axelor.db.JpaRepository;
import com.axelor.meta.db.MetaJsonRecord;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@XStreamAlias("input")
public class CSVInputJson extends CSVInput {

  @XStreamAlias("json-model")
  @XStreamAsAttribute
  private String jsonModel;

  private List<CSVBind> jsonBindings = new ArrayList<>();

  @Override
  public String getTypeName() {
    return MetaJsonRecord.class.getName();
  }

  public String getJsonModel() {
    return jsonModel;
  }

  public void setJsonModel(String jsonModel) {
    this.jsonModel = jsonModel;
  }

  @Override
  public String getSearch() {
    final String search = super.getSearch();

    if (StringUtils.isBlank(search)) {
      return search;
    }

    return Stream.of(search, "self.jsonModel = '%s'".formatted(getJsonModel()))
        .map("(%s)"::formatted)
        .collect(Collectors.joining(" AND "));
  }

  @Override
  public List<CSVBind> getBindings() {
    return Stream.concat(getJsonBindings().stream(), super.getBindings().stream())
        .collect(Collectors.toList());
  }

  private List<CSVBind> getJsonBindings() {
    if (jsonBindings == null) {
      final CSVBind jsonModelBinding = new CSVBind();
      jsonModelBinding.setField("jsonModel");
      jsonModelBinding.setExpression("'%s'".formatted(getJsonModel()));
      jsonBindings = Collections.singletonList(jsonModelBinding);
    }

    return jsonBindings;
  }

  @Override
  public Object postProcess(Object bean) {
    return JpaRepository.of(MetaJsonRecord.class).save((MetaJsonRecord) bean);
  }
}
