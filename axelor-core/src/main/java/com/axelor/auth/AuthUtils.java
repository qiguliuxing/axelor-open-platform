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

import com.axelor.auth.db.User;
import com.axelor.db.JPA;
import com.axelor.db.JpaRepository;
import com.axelor.db.QueryBinder;
import com.google.common.base.Preconditions;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.UnavailableSecurityManagerException;
import org.apache.shiro.session.InvalidSessionException;
import org.apache.shiro.subject.Subject;

public class AuthUtils {

  public static Subject getSubject() {
    try {
      return SecurityUtils.getSubject();
    } catch (UnavailableSecurityManagerException e) {
    }
    return null;
  }

  public static User getUser() {
    try {
      return getUser(getSubject().getPrincipal().toString());
    } catch (NullPointerException | InvalidSessionException e) {
    }
    return null;
  }

  public static User getUser(String code) {
    if (code == null) {
      return null;
    }
    return JpaRepository.of(User.class)
        .all()
        .filter("self.code = ?", code)
        .cacheable()
        .autoFlush(false)
        .fetchOne();
  }

  public static boolean isActive(final User user) {
    if (Boolean.TRUE.equals(user.getArchived()) || Boolean.TRUE.equals(user.getBlocked())) {
      return false;
    }

    final LocalDateTime from = user.getActivateOn();
    final LocalDateTime till = user.getExpiresOn();
    final LocalDateTime now = LocalDateTime.now();

    if ((from != null && from.isAfter(now)) || (till != null && till.isBefore(now))) {
      return false;
    }

    return true;
  }

  public static boolean isAdmin(final User user) {
    return "admin".equals(user.getCode())
        || (user.getGroup() != null && "admins".equals(user.getGroup().getCode()));
  }

  public static boolean isTechnicalStaff(final User user) {
    return user.getGroup() != null && Boolean.TRUE.equals(user.getGroup().getTechnicalStaff());
  }

  private static final String QS_HAS_ROLE =
      """
      SELECT self.id FROM Role self WHERE \
      (self.name IN (:roles)) AND \
      (\
        (self.id IN (SELECT r.id FROM User u LEFT JOIN u.roles AS r WHERE u.code = :user)) OR \
        (self.id IN (SELECT r.id FROM User u LEFT JOIN u.group AS g LEFT JOIN g.roles AS r WHERE u.code = :user))\
      )""";

  public static boolean hasRole(final User user, final String... roles) {
    Preconditions.checkArgument(user != null, "user not provided.");
    Preconditions.checkArgument(roles != null, "roles not provided.");
    Preconditions.checkArgument(roles.length > 0, "roles not provided.");
    final TypedQuery<Long> query = JPA.em().createQuery(QS_HAS_ROLE, Long.class);
    query.setParameter("roles", Arrays.asList(roles));
    query.setParameter("user", user.getCode());
    query.setMaxResults(1);

    QueryBinder.of(query).opts(true, FlushModeType.COMMIT);

    final List<Long> ids = query.getResultList();
    return ids != null && ids.size() == 1;
  }
}
