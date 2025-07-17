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
package com.axelor.db.hibernate.type;

import com.google.common.base.Preconditions;
import jakarta.persistence.PersistenceException;
import java.util.regex.Pattern;

public final class JsonFunction {

  public static final String DEFAULT_TYPE = "text";

  private static final Pattern NAME_PATTERN = Pattern.compile("[\\w_\\.]+");
  private static final Pattern TYPE_PATTERN =
      Pattern.compile("(text|boolean|integer|decimal)", Pattern.CASE_INSENSITIVE);

  private String field;

  private String prefix;

  private String attribute;

  private String type;

  public JsonFunction(String field, String attribute, String type, String prefix) {
    this.field = field;
    this.attribute = attribute;
    this.type = type;
    this.prefix = prefix;
  }

  public JsonFunction(String field, String attribute) {
    this(field, attribute, DEFAULT_TYPE, "self");
  }

  private static String validateField(String name) {
    if (NAME_PATTERN.matcher(name).matches()) {
      return name;
    }
    throw new PersistenceException("Invalid field name: " + name);
  }

  private static String validateType(String name) {
    if (TYPE_PATTERN.matcher(name).matches()) {
      return name;
    }
    throw new PersistenceException("Invalid json type: " + name);
  }

  public static JsonFunction fromPath(String path) {
    return fromPath("self", path);
  }

  public static JsonFunction fromPath(String prefix, String path) {
    Preconditions.checkArgument(path != null, "name cannot be null");
    Preconditions.checkArgument(path.indexOf('.') > -1, "not a json path");

    final int dot = path.indexOf('.');
    final int col = path.indexOf("::");

    final String type = col == -1 ? DEFAULT_TYPE : path.substring(col + 2);
    final String rest = col == -1 ? path : path.substring(0, col);
    final String field = rest.substring(0, dot);
    final String attribute = rest.substring(dot + 1);

    return "long".equals(type)
        ? new JsonFunction(field, attribute, "integer", prefix)
        : new JsonFunction(field, attribute, type, prefix);
  }

  public String getField() {
    return field;
  }

  public String getAttribute() {
    return attribute;
  }

  public String getType() {
    return type;
  }

  @Override
  public String toString() {
    final StringBuilder builder =
        new StringBuilder()
            .append("json_extract_")
            .append(validateType(type))
            .append("(")
            .append(prefix + ".")
            .append(validateField(field));
    for (String item : attribute.split("\\.")) {
      builder.append(", ").append("'").append(validateField(item)).append("'");
    }
    return builder.append(")").toString();
  }
}
