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
package com.axelor.web.servlet;

import com.axelor.app.internal.AppFilter;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.zip.GZIPOutputStream;
import jakarta.inject.Singleton;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Singleton
public class I18nServlet extends HttpServlet {

  private static final long serialVersionUID = -6879530734799286544L;

  private static final String CONTENT_ENCODING = "Content-Encoding";
  private static final String ACCEPT_ENCODING = "Accept-Encoding";
  private static final String GZIP_ENCODING = "gzip";
  private static final String CONTENT_TYPE = "application/json; charset=utf8";

  @Override
  protected void doGet(HttpServletRequest req, HttpServletResponse resp)
      throws ServletException, IOException {

    Locale locale = AppFilter.getLocale();
    if (locale == null) {
      locale = req.getLocale();
    }

    final ResourceBundle bundle = I18n.getBundle(locale);
    if (bundle == null) {
      resp.setStatus(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    resp.setContentType(CONTENT_TYPE);

    OutputStream out = resp.getOutputStream();
    if (req.getHeader(ACCEPT_ENCODING) != null
        && req.getHeader(ACCEPT_ENCODING).toLowerCase().indexOf(GZIP_ENCODING) > -1) {
      resp.setHeader(CONTENT_ENCODING, GZIP_ENCODING);
      out = new GZIPOutputStream(out);
    }

    Enumeration<String> keys = bundle.getKeys();
    Map<String, String> messages = new HashMap<>();

    while (keys.hasMoreElements()) {
      String key = keys.nextElement();
      messages.put(key, bundle.getString(key));
    }

    try {
      final ObjectMapper mapper = Beans.get(ObjectMapper.class);
      final String json = mapper.writeValueAsString(messages);
      out.write(json.getBytes(Charset.forName("UTF-8")));
    } catch (Exception e) {
      resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    } finally {
      out.close();
    }
  }
}
