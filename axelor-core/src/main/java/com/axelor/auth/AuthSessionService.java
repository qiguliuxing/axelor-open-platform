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
package com.axelor.auth;

import java.time.LocalDateTime;
import jakarta.annotation.Nullable;
import org.apache.shiro.session.InvalidSessionException;
import org.apache.shiro.session.Session;

/** Manages session attributes. */
public class AuthSessionService {
  private static final String LOGIN_DATE = "com.axelor.internal.loginDate";

  public void updateLoginDate() {
    updateLoginDate(AuthUtils.getSubject().getSession(false));
  }

  public void updateLoginDate(Session session) {
    if (session != null) {
      session.setAttribute(LOGIN_DATE, LocalDateTime.now());
    }
  }

  @Nullable
  public LocalDateTime getLoginDate() {
    return getLoginDate(AuthUtils.getSubject().getSession(false));
  }

  @Nullable
  public LocalDateTime getLoginDate(Session session) {
    try {
      if (session != null) {
        return (LocalDateTime) session.getAttribute(LOGIN_DATE);
      }
    } catch (InvalidSessionException e) {
      // Fall through
    }

    return null;
  }
}
