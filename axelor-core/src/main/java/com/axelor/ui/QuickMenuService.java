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
package com.axelor.ui;

import com.axelor.common.ObjectUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class QuickMenuService {

  private Set<QuickMenuCreator> handlers = new HashSet<>();

  @Inject
  public QuickMenuService(Set<QuickMenuCreator> handlers) {
    this.handlers = handlers;
  }

  public List<QuickMenu> get() {
    return handlers.stream()
        .map(QuickMenuCreator::create)
        .filter(menu -> menu != null && ObjectUtils.notEmpty(menu.getItems()))
        .sorted(Comparator.comparing(QuickMenu::getOrder))
        .collect(Collectors.toList());
  }
}
