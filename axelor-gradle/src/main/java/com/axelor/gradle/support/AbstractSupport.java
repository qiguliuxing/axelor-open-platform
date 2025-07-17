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
package com.axelor.gradle.support;

import com.axelor.common.ResourceUtils;
import com.google.common.io.CharStreams;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.dsl.DependencyHandler;

public abstract class AbstractSupport implements Plugin<Project> {

  protected void applyConfigurationLibs(Project project, String libs, String as) {
    final String path = "com/axelor/gradle/%s-libs.txt".formatted(libs);
    try (Reader reader = new InputStreamReader(ResourceUtils.getResourceStream(path))) {
      final DependencyHandler handler = project.getDependencies();
      CharStreams.readLines(reader).forEach(lib -> handler.add(as, lib));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
