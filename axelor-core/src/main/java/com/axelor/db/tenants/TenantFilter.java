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
package com.axelor.db.tenants;

import com.axelor.auth.pac4j.AuthPac4jInfo;
import com.axelor.common.StringUtils;
import com.axelor.inject.Beans;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import jakarta.inject.Singleton;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.core.HttpHeaders;
import org.pac4j.core.context.HttpConstants;

@Singleton
public class TenantFilter implements Filter {

  private static final String TENANT_COOKIE_NAME = "LAST-TENANT-ID";

  private static final String TENANT_ATTRIBUTE_NAME = "TENANT-ID";

  private static final String TENANT_HEADER_NAME = "X-Tenant-ID";

  private static final String PATH_CALLBACK = "/callback";

  private boolean enabled;

  @Override
  public void init(FilterConfig filterConfig) throws ServletException {
    enabled = TenantModule.isEnabled();
  }

  @Override
  public void destroy() {}

  @Override
  public final void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (enabled) {
      doFilterInternal(request, response, chain);
    } else {
      chain.doFilter(request, response);
    }
  }

  private void doFilterInternal(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {

    final HttpServletRequest req = (HttpServletRequest) request;
    final HttpServletResponse res = (HttpServletResponse) response;

    TenantResolver.CURRENT_HOST.set(req.getHeader("Host"));

    try {
      TenantResolver.CURRENT_TENANT.set(currentTenant(req, res));
      chain.doFilter(request, response);
    } catch (BadTenantException e) {

      // Only HTTP requests are intercepted in front-end.
      if (AuthPac4jInfo.isWebSocket(req)) {
        res.sendError(HttpServletResponse.SC_FORBIDDEN);
      } else {
        final HttpSession httpSession = req.getSession(false);

        if (httpSession != null) {
          httpSession.invalidate();
        }

        if (AuthPac4jInfo.isXHR(req)) {
          // Ajax request
          res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
          res.setContentType(HttpConstants.APPLICATION_JSON);
          response.setCharacterEncoding(StandardCharsets.UTF_8.name());
          final ObjectMapper mapper = Beans.get(ObjectMapper.class);
          res.getWriter().write(mapper.writeValueAsString(Map.of("status", 1)));
        } else {
          // Full page load
          res.sendRedirect(".");
        }
      }
    } finally {
      TenantResolver.CURRENT_HOST.remove();
      TenantResolver.CURRENT_TENANT.remove();
    }
  }

  // When tenant comes from header/cookie, need to check it.
  // For login, consider header only.
  private Optional<String> getRequestTenant(
      HttpServletRequest req, boolean isLogin, Map<String, String> tenants) {
    return Optional.ofNullable(req.getHeader(TENANT_HEADER_NAME))
        .filter(StringUtils::notBlank)
        .or(
            () ->
                isLogin
                    ? Optional.empty()
                    : Optional.ofNullable(getCookie(req, TENANT_COOKIE_NAME))
                        .map(Cookie::getValue)
                        .filter(StringUtils::notBlank))
        .filter(tenants::containsKey);
  }

  private String currentTenant(HttpServletRequest req, HttpServletResponse res)
      throws BadTenantException {
    final TenantInfo tenantInfo = TenantResolver.getTenantInfo(false);
    final String hostTenant = tenantInfo.getHostTenant();

    final HttpSession httpSession = req.getSession(false);
    final Optional<String> sessionTenant =
        Optional.ofNullable(httpSession)
            .map(session -> (String) session.getAttribute(TENANT_ATTRIBUTE_NAME));

    if (hostTenant != null) {
      if (httpSession != null && sessionTenant.isEmpty()) {
        httpSession.setAttribute(TENANT_ATTRIBUTE_NAME, hostTenant);
      }
      return hostTenant;
    }

    final boolean isLogin = PATH_CALLBACK.equals(req.getServletPath());
    final Map<String, String> tenants = tenantInfo.getTenants();
    final String tenant =
        sessionTenant
            .filter(t -> !isLogin)
            .or(() -> getRequestTenant(req, isLogin, tenants))
            .orElse(null);

    if (tenant == null && (isLogin || req.getHeader(HttpConstants.AUTHORIZATION_HEADER) != null)) {
      throw new BadTenantException();
    }

    if (httpSession != null && tenant != null) {
      if (!tenants.containsKey(tenant)) {
        throw new BadTenantException();
      } else if ((sessionTenant.isEmpty() || isLogin)) {
        httpSession.setAttribute(TENANT_ATTRIBUTE_NAME, tenant);
      }
    }

    if (isLogin) {
      setCookie(req, res, TENANT_COOKIE_NAME, tenant);
    }

    return tenant;
  }

  private Cookie getCookie(HttpServletRequest request, String name) {
    final Cookie[] cookies = request.getCookies();
    if (cookies != null) {
      for (Cookie cookie : cookies) {
        if (name.equals(cookie.getName())) {
          return cookie;
        }
      }
    }
    return null;
  }

  private void setCookie(
      HttpServletRequest request, HttpServletResponse response, String name, String value) {
    Cookie cookie = getCookie(request, name);

    if (cookie == null) {
      cookie = new Cookie(name, value);
    } else {
      cookie.setValue(value);
    }

    cookie.setHttpOnly(true);
    cookie.setMaxAge(60 * 60 * 24 * 7);

    if (request.isSecure()) {
      cookie.setSecure(true);
    }

    response.addCookie(cookie);

    if (cookie.getSecure()) {
      addSameSite(response, name);
    }
  }

  // With Jakarta Servlet API, we'll be able to use Cookie#setAttribute to set SameSite=None
  // Add SameSite=None attribute manually for now
  private void addSameSite(HttpServletResponse response, String name) {
    boolean first = true;
    for (String cookieString : response.getHeaders(HttpHeaders.SET_COOKIE)) {
      if (StringUtils.notEmpty(cookieString) && cookieString.startsWith(name)) {
        cookieString += "; SameSite=None";
      }
      if (first) {
        response.setHeader(HttpHeaders.SET_COOKIE, cookieString);
        first = false;
      } else {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieString);
      }
    }
  }
}
