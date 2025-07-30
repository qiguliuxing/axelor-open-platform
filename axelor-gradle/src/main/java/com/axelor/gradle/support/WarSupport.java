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

import com.axelor.gradle.tasks.CopyWebapp;
import com.axelor.gradle.tasks.GenerateCode;
import org.gradle.api.Project;
import org.gradle.api.file.DuplicatesStrategy;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.WarPlugin;
import org.gradle.api.tasks.bundling.War;

public class WarSupport extends AbstractSupport {

  public static final String COPY_WEBAPP_TASK_NAME = "copyWebapp";

  @Override
  public void apply(Project project) {

    project.getPlugins().apply(WarPlugin.class);

    // apply providedCompile dependencies
    applyConfigurationLibs(project, "provided", "compileOnly");

    // copy webapp to root build dir
    project
        .getTasks()
        .register(
            COPY_WEBAPP_TASK_NAME,
            CopyWebapp.class,
            task -> {
              task.dependsOn(GenerateCode.TASK_NAME);
              task.dependsOn(JavaPlugin.PROCESS_RESOURCES_TASK_NAME);
            });

    project.getTasks().withType(War.class).all(task -> task.dependsOn(COPY_WEBAPP_TASK_NAME));

    final War war = (War) project.getTasks().getByName(WarPlugin.WAR_TASK_NAME);
    war.from(project.getLayout().getBuildDirectory().dir("webapp"));
    war.exclude("**/.*");
    war.setDuplicatesStrategy(DuplicatesStrategy.EXCLUDE);
  }
}
