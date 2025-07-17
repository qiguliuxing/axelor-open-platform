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
package com.axelor.inject.logger;

import com.google.inject.Binding;
import com.google.inject.spi.ProvisionListener;
import java.util.ArrayDeque;

final class LoggerProvisionListener implements ProvisionListener {

  static final ThreadLocal<ArrayDeque<Binding<?>>> bindingStack =
      new ThreadLocal<>() {
        protected ArrayDeque<Binding<?>> initialValue() {
          return new ArrayDeque<>();
        }
      };

  @Override
  public <T> void onProvision(ProvisionInvocation<T> provision) {
    if (provision.getBinding().getSource() instanceof Class<?>) {
      try {
        bindingStack.get().push(provision.getBinding());
        provision.provision();
      } finally {
        bindingStack.get().pop();
      }
    }
  }
}
