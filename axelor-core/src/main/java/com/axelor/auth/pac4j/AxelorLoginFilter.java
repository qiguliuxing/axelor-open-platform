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

import com.axelor.auth.AuthUtils;
import jakarta.inject.Inject;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.pac4j.core.client.Clients;
import org.pac4j.core.profile.ProfileManager;
import org.pac4j.core.profile.UserProfile;
import org.pac4j.core.profile.factory.ProfileManagerFactory;
import org.pac4j.jee.context.JEEContext;
import org.pac4j.jee.context.session.JEESessionStore;
import org.pac4j.jee.filter.SecurityFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AxelorLoginFilter implements Filter {

  private final SecurityFilter securityFilter;
  private final ProfileManagerFactory profileManagerFactory;

  private static final Logger logger = LoggerFactory.getLogger(AxelorLoginFilter.class);

  @Inject
  public AxelorLoginFilter(
      AxelorSecurityFilter securityFilter, AxelorSecurityLogic securityLogic, Clients clients) {
    this.securityFilter = securityFilter;
    this.profileManagerFactory = securityLogic.getProfileManagerFactory();
  }

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    final Subject subject = SecurityUtils.getSubject();
    boolean authenticated = subject.isAuthenticated();

    if (authenticated) {
      if (AuthUtils.getUser() == null) {
        logger.warn("Authenticated, but no user: {}", subject.getPrincipal());
        subject.logout();
        authenticated = false;
      } else if (getUserProfile(request, response).isEmpty()) {
        logger.warn("Authenticated, but no user profile: {}", subject.getPrincipal());
        subject.logout();
        authenticated = false;
      }
    }

    // If already authenticated, redirect to base URL.
    if (authenticated) {
      ((HttpServletResponse) response).sendRedirect(".");
      return;
    }

    // When not authenticated, this triggers login process.
    securityFilter.doFilter(request, response, chain);
  }

  private Optional<UserProfile> getUserProfile(ServletRequest request, ServletResponse response) {
    final JEEContext context =
        new JEEContext((HttpServletRequest) request, (HttpServletResponse) response);
    final ProfileManager profileManager =
        profileManagerFactory.apply(context, new JEESessionStore());
    return profileManager.getProfile();
  }
}
