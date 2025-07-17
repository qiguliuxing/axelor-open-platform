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
package com.axelor.db;

import com.axelor.app.AppSettings;
import com.axelor.app.AvailableAppSettings;
import com.axelor.auth.AuditInterceptor;
import com.axelor.common.StringUtils;
import com.axelor.db.hibernate.dialect.CustomDialectResolver;
import com.axelor.db.hibernate.naming.ImplicitNamingStrategyImpl;
import com.axelor.db.hibernate.naming.PhysicalNamingStrategyImpl;
import com.axelor.db.internal.DBHelper;
import com.axelor.db.tenants.TenantConnectionProvider;
import com.axelor.db.tenants.TenantModule;
import com.axelor.db.tenants.TenantResolver;
import com.google.inject.AbstractModule;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.jpa.JpaPersistModule;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import com.google.inject.persist.jpa.JpaPersistOptions;
import jakarta.inject.Inject;
import org.hibernate.cache.jcache.ConfigSettings;
import org.hibernate.cfg.CacheSettings;
import org.hibernate.cfg.Environment;
import org.hibernate.hikaricp.internal.HikariCPConnectionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Guice module to configure JPA.
 *
 * <p>This module takes care of initializing JPA and registers an Hibernate custom scanner that
 * automatically scans all the classpath entries for Entity classes.
 */
public class JpaModule extends AbstractModule {

  private static Logger log = LoggerFactory.getLogger(JpaModule.class);

  private static final String DEFAULT_CACHE_REGION_FACTORY = "jcache";
  private static final String DEFAULT_JCACHE_PROVIDER =
      "com.github.benmanes.caffeine.jcache.spi.CaffeineCachingProvider";

  private String jpaUnit;
  private boolean autoscan;
  private boolean autostart;

  static {
    JpaScanner.exclude("com.axelor.test.db");
    JpaScanner.exclude("com.axelor.web.db");
  }

  /**
   * Create new instance of the {@link JpaModule} with the given persistence unit name.
   *
   * <p>If <i>autoscan</i> is true then a custom Hibernate scanner will be used to scan all the
   * classpath entries for Entity classes.
   *
   * <p>If <i>autostart</i> is true then the {@link PersistService} will be started automatically.
   *
   * @param jpaUnit the persistence unit name
   * @param autoscan whether to enable autoscan
   * @param autostart whether to automatically start persistence service
   */
  public JpaModule(String jpaUnit, boolean autoscan, boolean autostart) {
    this.jpaUnit = jpaUnit;
    this.autoscan = autoscan;
    this.autostart = autostart;
  }

  /**
   * Create a new instance of the {@link JpaModule} with the given persistence unit name with
   * <i>autoscan</i> and <i>autostart</i> enabled.
   *
   * @param jpaUnit the persistence unit name
   */
  public JpaModule(String jpaUnit) {
    this(jpaUnit, true, true);
  }

  public JpaModule scan(String pkg) {
    JpaScanner.include(pkg);
    return this;
  }

  @Override
  protected void configure() {
    log.debug("Configuring database...");

    final AppSettings settings = AppSettings.get();
    final Properties properties = new Properties();

    if (this.autoscan) {
      properties.put(Environment.SCANNER, JpaScanner.class.getName());
    }

    properties.put(Environment.INTERCEPTOR, AuditInterceptor.class.getName());
    properties.put(
        Environment.IMPLICIT_NAMING_STRATEGY, ImplicitNamingStrategyImpl.class.getName());
    properties.put(
        Environment.PHYSICAL_NAMING_STRATEGY, PhysicalNamingStrategyImpl.class.getName());
    properties.put(Environment.DIALECT_RESOLVERS, CustomDialectResolver.class.getName());

    properties.put(Environment.AUTOCOMMIT, "false");
    properties.put(Environment.MAX_FETCH_DEPTH, "3");

    if (!DBHelper.isDataSourceUsed()) {
      // Use HikariCP as default pool provider
      properties.put(Environment.CONNECTION_PROVIDER, HikariCPConnectionProvider.class.getName());
      properties.put(Environment.CONNECTION_PROVIDER_DISABLES_AUTOCOMMIT, "true");
      properties.put(AvailableAppSettings.HIBERNATE_HIKARI_MINIMUM_IDLE, "5");
      properties.put(AvailableAppSettings.HIBERNATE_HIKARI_MAXIMUM_POOL_SIZE, "20");
      properties.put(AvailableAppSettings.HIBERNATE_HIKARI_IDLE_TIMEOUT, "300000");
    }

    // update properties with all hibernate.* settings from app configuration
    settings
        .getPropertiesKeysStartingWith("hibernate.")
        .forEach(n -> properties.put(n, settings.get(n)));

    configureCache(settings, properties);
    configureMultiTenancy(settings, properties);

    try {
      configureConnection(settings, properties);
    } catch (Exception e) {
    }

    install(new TenantModule());
    JpaPersistOptions jpaPersistOpts = JpaPersistOptions.builder().setAutoBeginWorkOnEntityManagerCreation(true).build();
    install(new JpaPersistModule(jpaUnit, jpaPersistOpts).properties(properties));
    if (this.autostart) {
      bind(Initializer.class).asEagerSingleton();
    }
    bind(JPA.class).asEagerSingleton();
  }

  private void configureConnection(final AppSettings settings, final Properties properties) {
    if (DBHelper.isDataSourceUsed()) {
      properties.put(Environment.DATASOURCE, DBHelper.getDataSourceName());
      return;
    }

    final Map<String, String> keys = new HashMap<>();
    final String unit = jpaUnit.replaceAll("(PU|Unit)$", "").replaceAll("^persistence$", "default");

    keys.put("db.%s.ddl", Environment.HBM2DDL_AUTO);
    keys.put("db.%s.driver", Environment.JAKARTA_JDBC_DRIVER);
    keys.put("db.%s.url", Environment.JAKARTA_JDBC_URL);
    keys.put("db.%s.user", Environment.JAKARTA_JDBC_USER);
    keys.put("db.%s.password", Environment.JAKARTA_JDBC_PASSWORD);

    for (String key : keys.keySet()) {
      String name = keys.get(key);
      String value = settings.get(String.format(key, unit));
      if (!StringUtils.isBlank(value)) {
        properties.put(name, value.trim());
      }
    }
  }

  private void configureCache(final AppSettings settings, final Properties properties) {
    if (!DBHelper.isCacheEnabled()) {
      return;
    }

    properties.put(CacheSettings.JAKARTA_SHARED_CACHE_MODE, DBHelper.getSharedCacheMode());
    properties.put(Environment.USE_SECOND_LEVEL_CACHE, "true");
    properties.put(Environment.USE_QUERY_CACHE, "true");

    final String cacheRegionFactory =
        settings.get(AvailableAppSettings.HIBERNATE_CACHE_REGION_FACTORY);
    if (StringUtils.isBlank(cacheRegionFactory)
        || cacheRegionFactory.equals(DEFAULT_CACHE_REGION_FACTORY)) {
      properties.put(Environment.CACHE_REGION_FACTORY, DEFAULT_CACHE_REGION_FACTORY);
      final String jcacheProvider =
          settings.get(
              AvailableAppSettings.HIBERNATE_JAVAX_CACHE_PROVIDER, DEFAULT_JCACHE_PROVIDER);
      properties.put(ConfigSettings.PROVIDER, jcacheProvider);
      log.info("JCache provider: {}", jcacheProvider);
    } else {
      properties.put(Environment.CACHE_REGION_FACTORY, cacheRegionFactory);
      log.info("Cache region factory: {}", cacheRegionFactory);
    }
  }

  private void configureMultiTenancy(final AppSettings settings, final Properties properties) {
    // multi-tenancy support
    if (TenantModule.isEnabled()) {
      properties.put(
          Environment.MULTI_TENANT_CONNECTION_PROVIDER, TenantConnectionProvider.class.getName());
      properties.put(Environment.MULTI_TENANT_IDENTIFIER_RESOLVER, TenantResolver.class.getName());
    }
  }

  public static class Initializer {

    @Inject
    Initializer(PersistService service) {
      log.debug("Starting database service...");
      service.start();
    }
  }
}
