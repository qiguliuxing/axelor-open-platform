/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.internal;

import static com.axelor.common.StringUtils.isBlank;

import com.axelor.app.AppSettings;
import com.axelor.app.AvailableAppSettings;
import com.axelor.common.ResourceUtils;
import com.axelor.common.StringUtils;
import com.axelor.common.XMLUtils;
import jakarta.persistence.SharedCacheMode;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.xpath.XPath;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

/** This class provides some database helper methods (for internal use only). */
public class DBHelper {

  private static final Logger LOG = LoggerFactory.getLogger(DBHelper.class);

  private static Boolean unaccentSupport = null;

  private static final int DEFAULT_BATCH_SIZE = 20;
  private static final int DEFAULT_FETCH_SIZE = 20;

  private static final String UNACCENT_CHECK = "SELECT unaccent('text')";
  private static final String UNACCENT_CREATE = "CREATE EXTENSION IF NOT EXISTS unaccent";

  private static final String XPATH_ROOT = "/persistence/persistence-unit";

  private static final String XPATH_NON_JTA_DATA_SOURCE = "non-jta-data-source";
  private static final String XPATH_SHARED_CACHE_MODE = "shared-cache-mode";

  private static final String XPATH_PERSISTENCE_DRIVER =
      "properties/property[@name='jakarta.persistence.jdbc.driver']/@value";
  private static final String XPATH_PERSISTENCE_URL =
      "properties/property[@name='jakarta.persistence.jdbc.url']/@value";
  private static final String XPATH_PERSISTENCE_USER =
      "properties/property[@name='jakarta.persistence.jdbc.user']/@value";
  private static final String XPATH_PERSISTENCE_PASSWORD =
      "properties/property[@name='jakarta.persistence.jdbc.password']/@value";

  private static final String XPATH_BATCH_SIZE =
      "properties/property[@name='hibernate.jdbc.batch_size']/@value";
  private static final String XPATH_FETCH_SIZE =
      "properties/property[@name='hibernate.jdbc.fetch_size']/@value";

  private static String jndiName;
  private static String cacheMode;

  private static String jdbcDriver;
  private static String jdbcUrl;
  private static String jdbcUser;
  private static String jdbcPassword;

  private static int jdbcBatchSize;
  private static int jdbcFetchSize;

  static {
    initialize();
  }

  private DBHelper() {}

  private static String evaluate(XPath xpath, String base, String path, Document document) {
    try {
      return xpath.evaluate(base + "/" + path, document).trim();
    } catch (Exception e) {
    }
    return null;
  }

  private static void initialize() {

    final AppSettings settings = AppSettings.get();

    try (final InputStream res = ResourceUtils.getResourceStream("META-INF/persistence.xml")) {
      final XPath xpath = XMLUtils.createXPath();
      final DocumentBuilder db = XMLUtils.createDocumentBuilder();
      final Document document = db.parse(res);

      final String jpaUnit = evaluate(xpath, XPATH_ROOT, "@name", document);
      final String pu = jpaUnit.replaceAll("(PU|Unit)$", "").replaceAll("^persistence$", "default");

      if (StringUtils.isBlank(pu)) {
        throw new RuntimeException("Invalid persistence.xml, missing persistence unit name.");
      }

      final String configDataSource = "db.%s.datasource".formatted(pu);
      final String configDriver = "db.%s.driver".formatted(pu);
      final String configUrl = "db.%s.url".formatted(pu);
      final String configUser = "db.%s.user".formatted(pu);
      final String configPassword = "db.%s.password".formatted(pu);

      jndiName = settings.get(configDataSource);
      jdbcDriver = settings.get(configDriver);
      jdbcUrl = settings.get(configUrl);
      jdbcUser = settings.get(configUser);
      jdbcPassword = settings.get(configPassword);

      jdbcBatchSize =
          settings.getInt(AvailableAppSettings.HIBERNATE_JDBC_BATCH_SIZE, DEFAULT_BATCH_SIZE);
      jdbcFetchSize =
          settings.getInt(AvailableAppSettings.HIBERNATE_JDBC_FETCH_SIZE, DEFAULT_FETCH_SIZE);

      cacheMode = evaluate(xpath, XPATH_ROOT, XPATH_SHARED_CACHE_MODE, document);

      if (isBlank(jndiName)) {
        try {
          jdbcBatchSize = Integer.parseInt(evaluate(xpath, XPATH_ROOT, XPATH_BATCH_SIZE, document));
        } catch (Exception e) {
        }
        try {
          jdbcFetchSize = Integer.parseInt(evaluate(xpath, XPATH_ROOT, XPATH_FETCH_SIZE, document));
        } catch (Exception e) {
        }
      }

      if (isBlank(jndiName)) {
        jndiName = evaluate(xpath, XPATH_ROOT, XPATH_NON_JTA_DATA_SOURCE, document);
      }

      if (isBlank(jndiName) && isBlank(jdbcDriver)) {
        jdbcDriver = evaluate(xpath, XPATH_ROOT, XPATH_PERSISTENCE_DRIVER, document);
        jdbcUrl = evaluate(xpath, XPATH_ROOT, XPATH_PERSISTENCE_URL, document);
        jdbcUser = evaluate(xpath, XPATH_ROOT, XPATH_PERSISTENCE_USER, document);
        jdbcPassword = evaluate(xpath, XPATH_ROOT, XPATH_PERSISTENCE_PASSWORD, document);
      }
    } catch (Exception e) {
      LOG.error("Error when parsing database properties : ", e);
    }
  }

  /**
   * Get the JDBC connection configured for the application.
   *
   * <p>The connection is independent of JPA connection, so use carefully. It should be used only
   * when JPA context is not available.
   *
   * @return a {@link Connection}
   * @throws NamingException if configured JNDI name can't be resolved
   * @throws SQLException if connection can't be obtained
   * @throws ClassNotFoundException if JDBC driver is not found
   */
  public static Connection getConnection() throws NamingException, SQLException {

    if (!isBlank(jndiName)) {
      final DataSource ds = (DataSource) InitialContext.doLookup(jndiName);
      return ds.getConnection();
    }

    try {
      Class.forName(jdbcDriver);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword);
  }

  /** Run database migration scripts using flyway migration engine. */
  public static void migrate() {
    FluentConfiguration flywayConfiguration = Flyway.configure();
    if (!isBlank(jndiName)) {
      try {
        flywayConfiguration.dataSource((DataSource) InitialContext.doLookup(jndiName));
      } catch (NamingException e) {
        throw new FlywayException(e);
      }
    } else {
      flywayConfiguration.dataSource(jdbcUrl, jdbcUser, jdbcPassword);
    }
    flywayConfiguration.load().migrate();
  }

  public static String getDataSourceName() {
    return jndiName;
  }

  /** Check whether non-jta data source is used. */
  public static boolean isDataSourceUsed() {
    return !isBlank(jndiName);
  }

  /** Check whether shared cache (ie second-level cache) is enabled. */
  public static boolean isCacheEnabled() {
    SharedCacheMode sharedCacheMode = SharedCacheMode.NONE;
    try {
      sharedCacheMode = getSharedCacheMode();
    } catch (Exception e) {
      LOG.error("Shared cache mode is specified with an unknown value.");
    }
    switch (sharedCacheMode) {
      case ALL:
      case ENABLE_SELECTIVE:
      case DISABLE_SELECTIVE:
        return true;
      default:
        return false;
    }
  }

  /**
   * Returns the shared cache mode (ie second-level cache).<br/>
   * <br/>
   * The result of this method corresponds to the <code>jakarta.persistence.sharedCache.mode</code>
   * property in <code>axelor-config.properties<code/> if defined, else to the <code>shared-cache-mode</code>
   * element in the <code>persistence.xml</code> file.
   *
   * @return the second-level cache mode used
   */
  public static SharedCacheMode getSharedCacheMode() {
    final String mode =
        AppSettings.get().get(AvailableAppSettings.JAVAX_PERSISTENCE_SHARED_CACHE_MODE, cacheMode);
    return StringUtils.isBlank(mode) ? SharedCacheMode.NONE : SharedCacheMode.valueOf(mode);
  }

  /** Whether using oracle database. */
  public static boolean isOracle() {
    return isEngine(TargetDatabase.ORACLE);
  }

  /** Whether using MySQL database. */
  public static boolean isMySQL() {
    return isEngine(TargetDatabase.MYSQL);
  }

  /** Whether using PostgreSQL database. */
  public static boolean isPostgreSQL() {
    return isEngine(TargetDatabase.POSTGRESQL);
  }

  /** Whether using HSQL database. */
  public static boolean isHSQL() {
    return isEngine(TargetDatabase.HSQLDB.split("\\s+")[0]);
  }

  private static boolean isEngine(String engine) {
    return jdbcDriver != null && jdbcDriver.toLowerCase().contains(engine.toLowerCase());
  }

  public static String getJdbcDriver() {
    return jdbcDriver;
  }

  public static String getJdbcUrl() {
    return jdbcUrl;
  }

  public static String getJdbcUser() {
    return jdbcUser;
  }

  public static String getJdbcPassword() {
    return jdbcPassword;
  }

  /**
   * Get the jdbc batch size configured with <code>hibernate.jdbc.batch_size</code> property.
   *
   * @return batch size
   */
  public static int getJdbcBatchSize() {
    return jdbcBatchSize;
  }

  /**
   * Get the jdbc fetch size configured with <code>hibernate.jdbc.fetch_size</code> property.
   *
   * @return batch size
   */
  public static int getJdbcFetchSize() {
    return jdbcFetchSize;
  }

  /** Check whether the database has unaccent support. */
  public static boolean isUnaccentEnabled() {
    // TODO: add unaccent support for other database
    if (unaccentSupport == null && isPostgreSQL()) {
      try {
        unaccentSupport = testUnaccent();
      } catch (Exception e) {
        unaccentSupport = Boolean.FALSE;
      }
      if (Boolean.FALSE.equals(unaccentSupport)) {
        LOG.warn("unaccent extension is not supported by the database.");
      }
    }
    return Boolean.TRUE.equals(unaccentSupport);
  }

  private static boolean testUnaccent() throws Exception {
    Connection connection = getConnection();
    Statement stmt = connection.createStatement();
    try {
      try {
        stmt.executeQuery(UNACCENT_CHECK);
        return true;
      } catch (Exception e) {
      }
      try {
        stmt.executeUpdate(UNACCENT_CREATE);
        return true;
      } catch (Exception e) {
      }
    } finally {
      try {
        stmt.close();
      } catch (SQLException e) {
        // Ignored.
      }
      try {
        connection.close();
      } catch (SQLException e) {
        // Ignored.
      }
    }
    return false;
  }

  /**
   * Returns maximum number of workers.
   *
   * <p>Return the minimum between the JDBC connection max pool size and the number of processors
   * available to the Java virtual machine.
   *
   * @return maximum number of workers
   */
  public static int getMaxWorkers() {
    final AppSettings settings = AppSettings.get();
    final int maxPoolSize =
        settings.getInt(AvailableAppSettings.HIBERNATE_HIKARI_MAXIMUM_POOL_SIZE, 20);
    int maxWorkers = Runtime.getRuntime().availableProcessors();
    return Math.min(maxPoolSize, maxWorkers);
  }
}
