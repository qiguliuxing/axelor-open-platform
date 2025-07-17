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
package com.axelor.meta.schema.actions;

import com.axelor.app.internal.AppFilter;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.meta.ActionHandler;
import com.axelor.meta.MetaFiles;
import com.axelor.report.ReportGenerator;
import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.shiro.authz.UnauthorizedException;
import org.eclipse.birt.core.exception.BirtException;

public class ActionReport extends Action {

  private static final String DEFAULT_FORMAT = "pdf";

  @XmlType
  public static class Parameter extends ActionRecord.RecordField {}

  @XmlAttribute private String title;

  @XmlAttribute(name = "design")
  private String designName;

  @XmlAttribute(name = "output")
  private String outputName;

  @XmlAttribute private String format = DEFAULT_FORMAT;

  @XmlAttribute private Boolean attachment;

  @XmlElement(name = "param")
  private List<Parameter> parameters;

  @JsonGetter("title")
  public String getLocalizedTitle() {
    return I18n.get(title);
  }

  @JsonIgnore
  public String getTitle() {
    return title;
  }

  public String getDesignName() {
    return designName;
  }

  public String getOutputName() {
    return outputName;
  }

  public String getFormat() {
    return format;
  }

  public Boolean getAttachment() {
    return attachment;
  }

  public List<Parameter> getParameters() {
    return parameters;
  }

  @Override
  protected void checkPermission(ActionHandler handler) {
    if (handler.getContext() != null
        && StringUtils.notBlank(getModel())
        && !Objects.equals(
            handler.getContext().getContextClass(), handler.findModelClass(getModel()))) {
      log.error(
          "Model mismatching : ActionReport#model ({}) does not match Context#model ({})",
          getModel(),
          handler.getContext().getContextClass().toString());
      throw new UnauthorizedException(I18n.get("You are not allowed to execute this action."));
    }

    super.checkPermission(handler);

    if (attachment == Boolean.TRUE) {
      final Class<? extends Model> klass =
          handler.getContext().getContextClass().asSubclass(Model.class);
      final Long id = (Long) handler.getContext().get("id");
      if (id != null) {
        handler.checkPermission(JpaSecurity.AccessType.READ, klass, id);
      }
    }
  }

  private Object _evaluate(ActionHandler handler) throws IOException, BirtException {
    log.debug("action-report: {}", getName());

    final Map<String, Object> params = new HashMap<>();
    final ReportGenerator generator = Beans.get(ReportGenerator.class);

    if (parameters != null) {
      for (Parameter param : parameters) {
        if (param.test(handler)) {
          params.put(param.getName(), handler.evaluate(param.getExpression()));
        }
      }
    }

    log.debug("with params: {}", params);

    final Map<String, Object> result = new HashMap<>();
    final Class<?> klass = handler.getContext().getContextClass();
    final Long id = (Long) handler.getContext().get("id");

    final String outputName =
        this.outputName
            .replace("${date}", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")))
            .replace("${time}", LocalTime.now().format(DateTimeFormatter.ofPattern("HHmmss")))
            .replace("${name}", getName());

    final String fileName = "%s.%s".formatted(outputName, format);
    final File output = generator.generate(designName, format, params, AppFilter.getLocale());

    result.put("report", getName());
    result.put("reportFile", fileName);
    result.put("reportLink", output.getName());
    result.put("reportFormat", format);

    if (Boolean.TRUE.equals(attachment) && id != null) {
      final Model bean = (Model) JPA.em().find(klass, id);
      try (InputStream is = new FileInputStream(output)) {
        result.put("attached", Beans.get(MetaFiles.class).attach(is, fileName, bean).getMetaFile());
      }
    }

    return result;
  }

  @Override
  public Object evaluate(ActionHandler handler) {
    try {
      return _evaluate(handler);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
}
