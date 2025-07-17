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
import com.axelor.auth.db.repo.UserRepository;
import jakarta.inject.Inject;
import java.util.Objects;
import java.util.concurrent.Callable;

/** This class can be used to run batch jobs that requires to keep track of audit logs. */
public class AuditableRunner {

  static ThreadLocal<User> batchUser = new ThreadLocal<>();

  private static final String DEFAULT_BATCH_USER = "admin";

  private UserRepository users;

  @Inject
  public AuditableRunner(UserRepository users) {
    this.users = users;
  }

  /**
   * Run a batch job.
   *
   * @param job the job to run
   */
  public void run(final Runnable job) {
    try {
      run(
          new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
              job.run();
              return true;
            }
          });
    } catch (Exception e) {
      // propagate the exception
      throw new RuntimeException(e);
    }
  }

  /**
   * Run a batch job.
   *
   * @param <T> type of the result
   * @param job the job to run
   * @return job result
   * @throws Exception if unable to compute a result
   */
  public <T> T run(Callable<T> job) throws Exception {
    Objects.requireNonNull(job);
    Objects.requireNonNull(users);

    User user = AuthUtils.getUser();
    if (user == null) {
      user = users.findByCode(DEFAULT_BATCH_USER);
    }

    batchUser.set(user);
    try {
      return job.call();
    } finally {
      batchUser.remove();
    }
  }
}
