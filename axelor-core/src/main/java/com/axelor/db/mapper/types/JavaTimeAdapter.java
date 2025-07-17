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
package com.axelor.db.mapper.types;

import com.axelor.db.mapper.TypeAdapter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.Calendar;
import java.util.Date;

public class JavaTimeAdapter implements TypeAdapter<Object> {

  private DateTimeAdapter dateTimeAdapter = new DateTimeAdapter();
  private LocalDateAdapter localDateAdapter = new LocalDateAdapter();
  private LocalTimeAdapter localTimeAdapter = new LocalTimeAdapter();
  private LocalDateTimeAdapter localDateTimeAdapter = new LocalDateTimeAdapter();

  @Override
  public Object adapt(
      Object value, Class<?> actualType, Type genericType, Annotation[] annotations) {

    if (value == null || (value instanceof String stringValue && "".equals(stringValue.trim()))) {
      return null;
    }

    if (ZonedDateTime.class.isAssignableFrom(actualType))
      return dateTimeAdapter.adapt(value, actualType, genericType, annotations);

    if (LocalDate.class.isAssignableFrom(actualType))
      return localDateAdapter.adapt(value, actualType, genericType, annotations);

    if (LocalTime.class.isAssignableFrom(actualType))
      return localTimeAdapter.adapt(value, actualType, genericType, annotations);

    if (LocalDateTime.class.isAssignableFrom(actualType))
      return localDateTimeAdapter.adapt(value, actualType, genericType, annotations);

    return value;
  }

  public boolean isJavaTimeObject(Class<?> actualType) {
    return ZonedDateTime.class.isAssignableFrom(actualType)
        || LocalDate.class.isAssignableFrom(actualType)
        || LocalTime.class.isAssignableFrom(actualType)
        || LocalDateTime.class.isAssignableFrom(actualType);
  }

  private ZonedDateTime toZonedDateTime(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime;
    }
    if (value instanceof Date date) {
      return date.toInstant().atZone(ZoneId.systemDefault());
    }
    if (value instanceof Calendar calendar) {
      return calendar.toInstant().atZone(ZoneId.systemDefault());
    }
    try {
      return ZonedDateTime.from(((Temporal) value));
    } catch (Exception e) {
    }
    try {
      return OffsetDateTime.parse(value.toString()).atZoneSameInstant(ZoneId.systemDefault());
    } catch (Exception e) {
    }
    try {
      return LocalDateTime.parse(value.toString()).atZone(ZoneId.systemDefault());
    } catch (Exception e) {
    }
    try {
      return LocalDate.parse(value.toString())
          .atStartOfDay(ZoneOffset.UTC)
          .withZoneSameInstant(ZoneId.systemDefault());
    } catch (Exception e) {
    }
    try {
      return ZonedDateTime.parse(String.valueOf(value));
    } catch (Exception e) {
    }
    throw new IllegalArgumentException("Unable to convert value: " + value);
  }

  private LocalDate toLocalDate(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof LocalDate localDate) {
      return localDate;
    }
    if (value instanceof ZonedDateTime zonedDateTime) {
      return zonedDateTime.toLocalDate();
    }
    if (value instanceof Date date) {
      return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    if (value instanceof Calendar calendar) {
      return calendar.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
    }
    try {
      return LocalDate.parse(value.toString());
    } catch (Exception e) {
    }
    try {
      return LocalDateTime.parse(value.toString()).atZone(ZoneId.systemDefault()).toLocalDate();
    } catch (Exception e) {
    }
    try {
      return OffsetDateTime.parse(value.toString())
          .atZoneSameInstant(ZoneId.systemDefault())
          .toLocalDate();
    } catch (Exception e) {
    }
    try {
      return ZonedDateTime.from(((Temporal) value)).toLocalDate();
    } catch (Exception e) {
    }
    throw new IllegalArgumentException("Unable to convert value: " + value);
  }

  class DateTimeAdapter implements TypeAdapter<ZonedDateTime> {

    @Override
    public Object adapt(
        Object value, Class<?> actualType, Type genericType, Annotation[] annotations) {
      return toZonedDateTime(value);
    }
  }

  class LocalDateAdapter implements TypeAdapter<LocalDate> {

    @Override
    public Object adapt(
        Object value, Class<?> actualType, Type genericType, Annotation[] annotations) {
      return toLocalDate(value);
    }
  }

  class LocalTimeAdapter implements TypeAdapter<LocalTime> {

    @Override
    public Object adapt(
        Object value, Class<?> actualType, Type genericType, Annotation[] annotations) {
      try {
        return value instanceof LocalTime ? value : toZonedDateTime(value).toLocalTime();
      } catch (Exception e) {
        final ZonedDateTime dt = ZonedDateTime.now();
        final String val =
            "%d-%02d-%02dT%s"
                .formatted(dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth(), value);
        return toZonedDateTime(val).toLocalTime();
      }
    }
  }

  class LocalDateTimeAdapter implements TypeAdapter<LocalDateTime> {

    @Override
    public Object adapt(
        Object value, Class<?> actualType, Type genericType, Annotation[] annotations) {
      return value instanceof LocalDateTime ? value : toZonedDateTime(value).toLocalDateTime();
    }
  }
}
