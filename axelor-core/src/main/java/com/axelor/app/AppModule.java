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
package com.axelor.app;

import com.axelor.cache.CacheBuilder;
import com.axelor.db.audit.HibernateListenerConfigurator;
import com.axelor.event.EventModule;
import com.axelor.inject.Beans;
import com.axelor.inject.logger.LoggerModule;
import com.axelor.meta.MetaScanner;
import com.axelor.meta.db.repo.MetaJsonReferenceUpdater;
import com.axelor.meta.loader.ModuleManager;
import com.axelor.meta.loader.ViewObserver;
import com.axelor.meta.loader.ViewWatcherObserver;
import com.axelor.meta.service.ViewProcessor;
import com.axelor.meta.theme.MetaThemeService;
import com.axelor.meta.theme.MetaThemeServiceImpl;
import com.axelor.report.ReportEngineProvider;
import com.axelor.ui.QuickMenuCreator;
import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.birt.report.engine.api.IReportEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The application module scans the classpath and finds all the {@link AxelorModule} and installs
 * them in the dependency order.
 */
public class AppModule extends AbstractModule {

  private static Logger log = LoggerFactory.getLogger(AppModule.class);

  @Override
  protected void configure() {

    // initialize Beans helps
    bind(Beans.class).asEagerSingleton();

    // report engine
    bind(IReportEngine.class).toProvider(ReportEngineProvider.class);

    // Observe changes for views
    bind(ViewObserver.class);

    // Observe updates to fix m2o names in json values
    bind(MetaJsonReferenceUpdater.class);

    // Logger injection support
    install(new LoggerModule());

    // events support
    install(new EventModule());

    // Init QuickMenuCreator
    Multibinder.newSetBinder(binder(), QuickMenuCreator.class);

    // Hibernate listener configurator binder
    Multibinder.newSetBinder(binder(), HibernateListenerConfigurator.class);

    // View processor binder
    final Multibinder<ViewProcessor> viewProcessorBinder =
        Multibinder.newSetBinder(binder(), ViewProcessor.class);

    bind(AppSettingsObserver.class);
    bind(ViewWatcherObserver.class);

    bind(MetaThemeService.class).to(MetaThemeServiceImpl.class);

    final List<Class<? extends AxelorModule>> moduleClasses =
        ModuleManager.getResolution().stream()
            .flatMap(name -> MetaScanner.findSubTypesOf(name, AxelorModule.class).find().stream())
            .collect(Collectors.toList());

    if (moduleClasses.isEmpty()) {
      return;
    }

    log.info("Configuring app modules...");

    for (Class<? extends AxelorModule> module : moduleClasses) {
      try {
        log.debug("Configure module: {}", module.getName());
        install(module.getDeclaredConstructor().newInstance());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    // Configure view processors

    final List<Class<? extends ViewProcessor>> viewProcessorClasses =
        ModuleManager.getResolution().stream()
            .flatMap(name -> MetaScanner.findSubTypesOf(name, ViewProcessor.class).find().stream())
            .collect(Collectors.toList());

    if (!viewProcessorClasses.isEmpty()) {
      log.atInfo()
          .setMessage("View processors: {}")
          .addArgument(
              () ->
                  viewProcessorClasses.stream()
                      .map(Class::getSimpleName)
                      .collect(Collectors.joining(", ")))
          .log();
      viewProcessorClasses.forEach(
          viewProcessor -> viewProcessorBinder.addBinding().to(viewProcessor));
    }

    var cacheProviderInfo = CacheBuilder.getCacheProviderInfo();
    log.info("Cache provider: {}", cacheProviderInfo.getProvider());
  }
}
