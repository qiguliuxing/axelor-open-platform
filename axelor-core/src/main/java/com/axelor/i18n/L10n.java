/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.i18n;

import com.axelor.app.internal.AppFilter;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.chrono.IsoChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.FormatStyle;
import java.util.Locale;

/** This class provider methods for localization (L10n) services. */
public final class L10n {

  private final NumberFormat numberFormat;
  private final DateTimeFormatter dateFormatter;
  private final DateTimeFormatter timeFormatter;
  private final DateTimeFormatter dateTimeFormatter;

  private L10n(Locale locale) {
    final String datePattern =
        getPatternWithFullYear(
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, null, IsoChronology.INSTANCE, locale));
    final String timePattern =
        DateTimeFormatterBuilder.getLocalizedDateTimePattern(
            null, FormatStyle.SHORT, IsoChronology.INSTANCE, locale);
    final String dateTimePattern =
        getPatternWithFullYear(
            DateTimeFormatterBuilder.getLocalizedDateTimePattern(
                FormatStyle.SHORT, FormatStyle.SHORT, IsoChronology.INSTANCE, locale));

    dateFormatter = DateTimeFormatter.ofPattern(datePattern);
    timeFormatter = DateTimeFormatter.ofPattern(timePattern);
    dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimePattern);
    numberFormat = NumberFormat.getInstance(locale);
  }

  /** Get pattern with 4-digit year. */
  private String getPatternWithFullYear(String pattern) {
    return pattern.replaceAll("\\by+\\b", "yyyy").replaceAll("\\bu+\\b", "uuuu");
  }

  /**
   * Get instance of {@link L10n} using contextual locale.
   *
   * @return {@link L10n} instance for the context
   */
  public static L10n getInstance() {
    final Locale locale = AppFilter.getLocale();
    return new L10n(locale);
  }

  /**
   * Get instance of {@link L10n} for the given locale.
   *
   * @param locale the locale instance
   * @return {@link L10n} instance for the given locale
   */
  public static L10n getInstance(Locale locale) {
    return new L10n(locale);
  }

  /**
   * Format the number value.
   *
   * @param value the value to format
   * @return value as formated string
   */
  public String format(Number value) {
    return format(value, true);
  }

  /**
   * Format the number value.
   *
   * @param value the value to format
   * @param grouping whether to use grouping in format
   * @return value as formated string
   */
  public String format(Number value, boolean grouping) {
    if (value == null) {
      return null;
    }
    numberFormat.setGroupingUsed(grouping);
    return numberFormat.format(value);
  }

  /**
   * Format the date value.
   *
   * @param value the value to format
   * @return value as formated string
   */
  public String format(LocalDate value) {
    if (value == null) {
      return null;
    }
    return dateFormatter.format(value);
  }

  /**
   * Format the time value.
   *
   * @param value the value to format
   * @return value as formated string
   */
  public String format(LocalTime value) {
    if (value == null) {
      return null;
    }
    return timeFormatter.format(value);
  }

  /**
   * Format the date time value.
   *
   * @param value the value to format
   * @return value as formated string
   */
  public String format(LocalDateTime value) {
    if (value == null) {
      return null;
    }
    return dateTimeFormatter.format(value);
  }

  /**
   * Format the date time value.
   *
   * @param value the value to format
   * @return value as formated string
   */
  public String format(ZonedDateTime value) {
    if (value == null) {
      return null;
    }
    return dateTimeFormatter.format(value);
  }
}
