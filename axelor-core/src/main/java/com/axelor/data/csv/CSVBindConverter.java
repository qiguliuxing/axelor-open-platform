/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.data.csv;

import com.axelor.common.StringUtils;
import com.axelor.db.mapper.JsonProperty;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import java.util.Optional;

public class CSVBindConverter implements Converter {

  private final Converter defaultConverter;
  private final ReflectionProvider reflectionProvider;

  public CSVBindConverter(Converter defaultConverter, ReflectionProvider reflectionProvider) {
    this.defaultConverter = defaultConverter;
    this.reflectionProvider = reflectionProvider;
  }

  @Override
  public boolean canConvert(@SuppressWarnings("rawtypes") Class type) {
    return CSVBind.class.isAssignableFrom(type);
  }

  @Override
  public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
    throw new UnsupportedOperationException();
  }

  @Override
  public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
    final String field = reader.getAttribute("to");

    return Optional.ofNullable(field).orElse("").startsWith(JsonProperty.KEY_JSON_PREFIX)
        ? newInstanceJson(context, reader.getAttribute("json-model"))
        : newInstance(context);
  }

  private Object newInstance(UnmarshallingContext context) {
    return newInstance(CSVBind.class, context);
  }

  private Object newInstanceJson(UnmarshallingContext context, String jsonModel) {
    final Object result = newInstance(CSVBindJson.class, context);

    if (StringUtils.notBlank(jsonModel)) {
      reflectionProvider.writeField(result, "jsonModel", jsonModel, CSVBindJson.class);
    }

    return result;
  }

  private Object newInstance(Class<?> resultType, UnmarshallingContext context) {
    final Object result = reflectionProvider.newInstance(resultType);
    return context.convertAnother(result, resultType, defaultConverter);
  }
}
