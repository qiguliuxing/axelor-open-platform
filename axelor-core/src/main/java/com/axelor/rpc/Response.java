/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.rpc;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.NumberSerializer;
import com.google.common.base.Throwables;
import java.sql.BatchUpdateException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@JsonInclude(Include.NON_EMPTY)
public class Response {

  public static int STATUS_FAILURE = -1;
  public static int STATUS_LOGIN_INCORRECT = -5;
  public static int STATUS_LOGIN_REQUIRED = -7;
  public static int STATUS_LOGIN_SUCCESS = -8;
  public static int STATUS_MAX_LOGIN_ATTEMPTS_EXCEEDED = -6;
  public static int STATUS_SERVER_TIMEOUT = -100;
  public static int STATUS_SUCCESS = 0;
  public static int STATUS_TRANSPORT_ERROR = -90;
  public static int STATUS_VALIDATION_ERROR = -4;

  @SuppressWarnings("serial")
  private static class OffsetSerializer extends NumberSerializer {

    public OffsetSerializer() {
      super(Integer.class);
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Number value) {
      return value == null || value.intValue() == -1;
    }
  }

  @SuppressWarnings("serial")
  private static class TotalSerializer extends NumberSerializer {

    public TotalSerializer() {
      super(Long.class);
    }

    @Override
    public boolean isEmpty(SerializerProvider provider, Number value) {
      return value == null || value.longValue() == -1;
    }
  }

  private int status;

  @JsonSerialize(using = OffsetSerializer.class)
  private int offset = -1;

  @JsonSerialize(using = TotalSerializer.class)
  private long total = -1;

  private Object data;

  private Map<String, String> errors;

  public int getStatus() {
    return status;
  }

  public void setStatus(int status) {
    this.status = status;
  }

  public int getOffset() {
    return offset;
  }

  public void setOffset(int offset) {
    this.offset = offset;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long count) {
    this.total = count;
  }

  public Object getData() {
    return data;
  }

  public void setData(Object data) {
    this.data = data;
  }

  public Map<String, String> getErrors() {
    return errors;
  }

  public void setErrors(Map<String, String> errors) {
    this.errors = errors;
  }

  public void addError(String fieldName, String errorMessage) {
    if (this.errors == null) {
      this.errors = new HashMap<>();
    }
    this.errors.put(fieldName, errorMessage);
  }

  public Response fail(String message) {
    final ResponseException error = new ResponseException(message);
    this.setException(error);
    return this;
  }

  public void setException(Throwable throwable) {
    if (throwable instanceof ResponseException error) {
      this.setData(error.toReport());
      this.setStatus(STATUS_FAILURE);
      return;
    }

    Throwable cause = Throwables.getRootCause(throwable);
    if (cause instanceof BatchUpdateException exception) {
      cause = exception.getNextException();
    }

    String message = throwable.getMessage();
    if (message == null || message.startsWith(cause.getClass().getName())) {
      message = cause.getMessage();
    }

    ResponseException error = new ResponseException(message, cause);

    this.setData(error.toReport());
    this.setStatus(STATUS_FAILURE);
  }

  public Object getItem(int index) {
    try {
      return ((List<?>) data).get(index);
    } catch (Exception e) {
    }
    return null;
  }
}
