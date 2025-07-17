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
package com.axelor.auth.pac4j;

import com.axelor.app.AppSettings;
import com.axelor.app.AvailableAppSettings;
import com.axelor.auth.AuthUtils;
import com.axelor.auth.pac4j.config.LogoutConfig;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Optional;
import org.apache.shiro.subject.Subject;
import org.pac4j.jee.filter.LogoutFilter;

@Singleton
public class AxelorLogoutFilter extends LogoutFilter {

  @Inject
  public AxelorLogoutFilter(LogoutConfig config, AxelorLogoutFilterConfig filterConfig)
      throws ServletException {

    final AppSettings settings = AppSettings.get();
    final String logoutUrlPattern =
        settings.get(AvailableAppSettings.AUTH_LOGOUT_URL_PATTERN, null);

    setConfig(config);
    setLogoutUrlPattern(logoutUrlPattern);

    init(filterConfig);
  }

  @Override
  public void doFilter(
      ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
      throws IOException, ServletException {

    super.doFilter(servletRequest, servletResponse, filterChain);

    if (!Boolean.FALSE.equals(getLocalLogout())) {
      // Log out subject.
      Optional.ofNullable(AuthUtils.getSubject()).ifPresent(Subject::logout);

      // Destroy web session.
      final HttpSession session = ((HttpServletRequest) servletRequest).getSession(false);
      if (session != null) {
        session.invalidate();
      }
    }
  }
}
