/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.app;

import org.hibernate.cache.jcache.ConfigSettings;
import org.hibernate.cfg.CacheSettings;

public interface AvailableAppSettings {

  String APPLICATION_NAME = "application.name";
  String APPLICATION_DESCRIPTION = "application.description";
  String APPLICATION_HOME = "application.home";
  String APPLICATION_VERSION = "application.version";
  String APPLICATION_AUTHOR = "application.author";
  String APPLICATION_LOCALE = "application.locale";
  String APPLICATION_THEME = "application.theme";
  String APPLICATION_HELP = "application.help";
  String APPLICATION_COPYRIGHT = "application.copyright";
  String APPLICATION_LOGO = "application.logo";
  String APPLICATION_LOGO_DARK = "application.logo-dark";
  String APPLICATION_ICON = "application.icon";
  String APPLICATION_ICON_DARK = "application.icon-dark";
  String APPLICATION_MODE = "application.mode";
  String APPLICATION_BASE_URL = "application.base-url";
  String APPLICATION_CONFIG_PROVIDER = "application.config-provider";
  String CONFIG_MULTI_TENANCY = "application.multi-tenancy";
  String APPLICATION_POLLING_INTERVAL = "application.polling-interval";

  @Deprecated
  String APPLICATION_PERMISSION_DISABLE_RELATIONAL_FIELD =
      "application.permission.disable-relational-field";

  @Deprecated
  String APPLICATION_PERMISSION_DISABLE_ACTION = "application.permission.disable-action";

  String APPLICATION_SCRIPT_CACHE_SIZE = "application.script.cache.size";
  String APPLICATION_SCRIPT_CACHE_EXPIRE_TIME = "application.script.cache.expire-time";

  String APPLICATION_DOMAIN_BLOCKLIST_PATTERN = "application.domain-blocklist-pattern";

  String APPLICATION_OPENAPI_ENABLED = "application.openapi.enabled";
  String APPLICATION_OPENAPI_SCAN_EXCLUDE_PACKAGES = "application.openapi.scan.exclude.packages";
  String APPLICATION_OPENAPI_SCAN_EXCLUDE_CLASSES = "application.openapi.scan.exclude.classes";
  String APPLICATION_OPENAPI_SCAN_PACKAGES = "application.openapi.scan.packages";
  String APPLICATION_OPENAPI_SCAN_CLASSES = "application.openapi.scan.classes";
  String APPLICATION_SWAGGER_UI_ENABLED = "application.swagger-ui.enabled";
  String APPLICATION_SWAGGER_UI_ALLOW_TRY_IT_OUT = "application.swagger-ui.allow-try-it-out";

  String APPLICATION_SIGN_IN_PREFIX = "application.sign-in.";

  String APPLICATION_RESET_PASSWORD_ENABLED = "application.reset-password.enabled";
  String APPLICATION_RESET_PASSWORD_MAX_AGE = "application.reset-password.max-age";

  String APPLICATION_CACHE_PROVIDER = "application.cache.provider";
  String APPLICATION_CACHE_CONFIG_PREFIX = "application.cache.config.";
  String APPLICATION_CACHE_CONFIG_PATH = APPLICATION_CACHE_CONFIG_PREFIX + "path";

  String APPLICATION_CACHE_HIBERNATE_PROVIDER = "application.cache.hibernate.provider";
  String APPLICATION_CACHE_HIBERNATE_CONFIG_PREFIX = "application.cache.hibernate.config.";
  String APPLICATION_CACHE_HIBERNATE_CONFIG_PATH =
      APPLICATION_CACHE_HIBERNATE_CONFIG_PREFIX + "path";

  String APPLICATION_CACHE_SHIRO_PROVIDER = "application.cache.shiro.provider";
  String APPLICATION_CACHE_SHIRO_CONFIG_PREFIX = "application.cache.shiro.config.";
  String APPLICATION_CACHE_SHIRO_CONFIG_PATH = APPLICATION_CACHE_SHIRO_CONFIG_PREFIX + "path";

  String VIEW_SINGLE_TAB = "view.single-tab";
  String VIEW_TABS_MAX = "view.max-tabs";
  String VIEW_CUSTOMIZATION = "view.allow-customization";

  String VIEW_FORM_CHECK_VERSION = "view.form.check-version";

  String VIEW_ADV_SEARCH_EXPORT_FULL = "view.adv-search.export-full";
  String VIEW_ADV_SEARCH_SHARE = "view.adv-search.share";

  String VIEW_GRID_SELECTION = "view.grid.selection";

  String API_PAGINATION_MAX_PER_PAGE = "api.pagination.max-per-page";
  String API_PAGINATION_DEFAULT_PER_PAGE = "api.pagination.default-per-page";

  String CONTEXT_APP_LOGO = "context.appLogo";
  String CONTEXT_APP_ICON = "context.appIcon";

  String DB_DEFAULT_DATASOURCE = "db.default.datasource";
  String DB_DEFAULT_DRIVER = "db.default.driver";
  String DB_DEFAULT_URL = "db.default.url";
  String DB_DEFAULT_USER = "db.default.user";
  String DB_DEFAULT_PASSWORD = "db.default.password";

  String REPORTS_DESIGN_DIR = "reports.design-dir";
  String REPORTS_FONTS_CONFIG = "reports.fonts-config";

  String TEMPLATE_SEARCH_DIR = "template.search-dir";

  String DATA_UPLOAD_DIR = "data.upload.dir";
  String DATA_UPLOAD_TEMP_DIR = "data.upload.temp-dir";
  String FILE_UPLOAD_SIZE = "data.upload.max-size";
  String FILE_UPLOAD_FILENAME_PATTERN = "data.upload.filename-pattern";
  String FILE_UPLOAD_WHITELIST_PATTERN = "data.upload.allowlist.pattern";
  String FILE_UPLOAD_BLACKLIST_PATTERN = "data.upload.blocklist.pattern";
  String FILE_UPLOAD_WHITELIST_TYPES = "data.upload.allowlist.types";
  String FILE_UPLOAD_BLACKLIST_TYPES = "data.upload.blocklist.types";

  String DATA_EXPORT_MAX_SIZE = "data.export.max-size";
  String DATA_EXPORT_FETCH_SIZE = "data.export.fetch-size";
  String DATA_EXPORT_ENCODING = "data.export.encoding";
  String DATA_EXPORT_LOCALE = "data.export.locale";
  String DATA_EXPORT_SEPARATOR = "data.export.separator";
  String DATA_EXPORT_COLLECTIONS_ENABLED = "data.export.collections.enabled";
  String DATA_EXPORT_COLLECTIONS_SEPARATOR = "data.export.collections.separator";

  String DATA_IMPORT_DEMO_DATA = "data.import.demo-data";

  String DATA_OBJECT_STORAGE_ENABLED = "data.object-storage.enabled";
  String DATA_OBJECT_STORAGE_ENDPOINT = "data.object-storage.endpoint";
  String DATA_OBJECT_STORAGE_PATH_STYLE = "data.object-storage.path-style";
  String DATA_OBJECT_STORAGE_SECURE = "data.object-storage.secure";
  String DATA_OBJECT_STORAGE_ACCESS_KEY = "data.object-storage.access-key";
  String DATA_OBJECT_STORAGE_SECRET_KEY = "data.object-storage.secret-key";
  String DATA_OBJECT_STORAGE_BUCKET = "data.object-storage.bucket";
  String DATA_OBJECT_STORAGE_REGION = "data.object-storage.region";
  String DATA_OBJECT_STORAGE_ENCRYPTION = "data.object-storage.encryption";
  String DATA_OBJECT_STORAGE_ENCRYPTION_KMS_KEY_ID = "data.object-storage.encryption-kms-key-id";
  String DATA_OBJECT_STORAGE_STORAGE_CLASS = "data.object-storage.storage-class";

  String DATA_OBJECT_STORAGE_AWS_CONFIG_FILENAME = "data.object-storage.aws-config.filename";
  String DATA_OBJECT_STORAGE_AWS_CONFIG_PROFILE = "data.object-storage.aws-config.profile";
  String DATA_OBJECT_STORAGE_IAM_AWS_CUSTOM_ENDPOINT =
      "data.object-storage.iam-aws.custom-endpoint";

  String DATA_OBJECT_STORAGE_CACHE_ENABLED = "data.object-storage.cache.enabled";
  String DATA_OBJECT_STORAGE_CACHE_MAX_ENTRIES = "data.object-storage.cache.max-entries";
  String DATA_OBJECT_STORAGE_CACHE_TIME_TO_LIVE = "data.object-storage.cache.time-to-live";
  String DATA_OBJECT_STORAGE_CACHE_CLEAN_FREQUENCY = "data.object-storage.cache.clean-frequency";

  String CORS_ALLOW_ORIGIN = "cors.allow-origin";
  String CORS_ALLOW_CREDENTIALS = "cors.allow-credentials";
  String CORS_ALLOW_METHODS = "cors.allow-methods";
  String CORS_ALLOW_HEADERS = "cors.allow-headers";
  String CORS_EXPOSE_HEADERS = "cors.expose-headers";
  String CORS_MAX_AGE = "cors.max-age";

  String SESSION_TIMEOUT = "session.timeout";
  String SESSION_COOKIE_SECURE = "session.cookie.secure";

  String QUARTZ_ENABLE = "quartz.enable";
  String QUARTZ_THREAD_COUNT = "quartz.thread-count";
  String QUARTZ_JOB_STORE_PREFIX = "quartz.job-store.";
  String QUARTZ_JOB_STORE_CLASS = QUARTZ_JOB_STORE_PREFIX + "class";
  String QUARTZ_DATA_SOURCE_PREFIX = "quartz.data-source.";

  String USER_PASSWORD_PATTERN = "user.password.pattern";
  String USER_PASSWORD_PATTERN_TITLE = /*$$(*/ "user.password.pattern-title" /*)*/;

  String ENCRYPTION_ALGORITHM = "encryption.algorithm";
  String ENCRYPTION_PASSWORD = "encryption.password";
  String ENCRYPTION_OLD_ALGORITHM = "encryption.old-algorithm";
  String ENCRYPTION_OLD_PASSWORD = "encryption.old-password";

  String HIBERNATE_HIKARI_MINIMUM_IDLE = "hibernate.hikari.minimumIdle";
  String HIBERNATE_HIKARI_MAXIMUM_POOL_SIZE = "hibernate.hikari.maximumPoolSize";
  String HIBERNATE_HIKARI_IDLE_TIMEOUT = "hibernate.hikari.idleTimeout";

  String HIBERNATE_JDBC_BATCH_SIZE = "hibernate.jdbc.batch_size";
  String HIBERNATE_JDBC_FETCH_SIZE = "hibernate.jdbc.fetch_size";

  String HIBERNATE_CACHE_REGION_FACTORY = CacheSettings.CACHE_REGION_FACTORY;
  String HIBERNATE_CACHE_REGION_PREFIX = CacheSettings.CACHE_REGION_PREFIX;
  String HIBERNATE_JAVAX_CACHE_PROVIDER = ConfigSettings.PROVIDER;

  String JAVAX_PERSISTENCE_SHARED_CACHE_MODE = CacheSettings.JAKARTA_SHARED_CACHE_MODE;

  String MAIL_SMTP_HOST = "mail.smtp.host";
  String MAIL_SMTP_PORT = "mail.smtp.port";
  String MAIL_SMTP_USER = "mail.smtp.user";
  String MAIL_SMTP_PASSWORD = "mail.smtp.password";
  String MAIL_SMTP_CHANNEL = "mail.smtp.channel";
  String MAIL_SMTP_TIMEOUT = "mail.smtp.timeout";
  String MAIL_SMTP_CONNECTION_TIMEOUT = "mail.smtp.connection-timeout";
  String MAIL_SMTP_FROM = "mail.smtp.from";
  String MAIL_SMTP_PROPERTIES_PREFIX = "mail.smtp.properties.";

  String MAIL_IMAP_HOST = "mail.imap.host";
  String MAIL_IMAP_PORT = "mail.imap.port";
  String MAIL_IMAP_USER = "mail.imap.user";
  String MAIL_IMAP_PASSWORD = "mail.imap.password";
  String MAIL_IMAP_CHANNEL = "mail.imap.channel";
  String MAIL_IMAP_TIMEOUT = "mail.imap.timeout";
  String MAIL_IMAP_CONNECTION_TIMEOUT = "mail.imap.connection-timeout";
  String MAIL_IMAP_PROPERTIES_PREFIX = "mail.imap.properties.";

  String LOGGING_PATH = "logging.path";
  String LOGGING_CONFIG = "logging.config";
  String LOGGING_PATTERN_FILE = "logging.pattern.file";
  String LOGGING_PATTERN_CONSOLE = "logging.pattern.console";

  String AUTH_CALLBACK_URL = "auth.callback-url";

  String AUTH_USER_PROVISIONING = "auth.user.provisioning";
  String AUTH_USER_DEFAULT_GROUP = "auth.user.default-group";
  String AUTH_USER_PRINCIPAL_ATTRIBUTE = "auth.user.principal-attribute";

  String AUTH_LOGOUT_DEFAULT_URL = "auth.logout.default-url";
  String AUTH_LOGOUT_URL_PATTERN = "auth.logout.url-pattern";

  String AUTH_LOCAL_BASIC_AUTH = "auth.local.basic-auth";

  String AUTH_LDAP_SERVER_URL = "auth.ldap.server.url";
  String AUTH_LDAP_SERVER_CONNECT_TIMEOUT = "auth.ldap.server.connect-timeout";
  String AUTH_LDAP_SERVER_RESPONSE_TIMEOUT = "auth.ldap.server.response-timeout";
  String AUTH_LDAP_SERVER_STARTTLS = "auth.ldap.server.starttls";
  String AUTH_LDAP_SERVER_AUTH_USER = "auth.ldap.server.auth.user";
  String AUTH_LDAP_SERVER_AUTH_PASSWORD = "auth.ldap.server.auth.password";
  String AUTH_LDAP_SERVER_AUTH_TYPE = "auth.ldap.server.auth.type";

  String AUTH_LDAP_USER_BASE = "auth.ldap.user.base";
  String AUTH_LDAP_USER_FILTER = "auth.ldap.user.filter";
  String AUTH_LDAP_USER_DN_FORMAT = "auth.ldap.user.dn-format";
  String AUTH_LDAP_USER_ID_ATTRIBUTE = "auth.ldap.user.id-attribute";
  String AUTH_LDAP_USER_USERNAME_ATTRIBUTE = "auth.ldap.user.username-attribute";

  String AUTH_LDAP_GROUP_BASE = "auth.ldap.group.base";
  String AUTH_LDAP_GROUP_FILTER = "auth.ldap.group.filter";

  String AUTH_LDAP_SERVER_SSL_TRUST_STORE_PATH = "auth.ldap.server.ssl.trust-store.path";
  String AUTH_LDAP_SERVER_SSL_TRUST_STORE_PASSWORD = "auth.ldap.server.ssl.trust-store.password";
  String AUTH_LDAP_SERVER_SSL_TRUST_STORE_TYPE = "auth.ldap.server.ssl.trust-store.type";
  String AUTH_LDAP_SERVER_SSL_TRUST_STORE_ALIASES = "auth.ldap.server.ssl.trust-store.aliases";

  String AUTH_LDAP_SERVER_SSL_KEY_STORE_PATH = "auth.ldap.server.ssl.key-store.path";
  String AUTH_LDAP_SERVER_SSL_KEY_STORE_PASSWORD = "auth.ldap.server.ssl.key-store.password";
  String AUTH_LDAP_SERVER_SSL_KEY_STORE_TYPE = "auth.ldap.server.ssl.key-store.type";
  String AUTH_LDAP_SERVER_SSL_KEY_STORE_ALIASES = "auth.ldap.server.ssl.key-store.aliases";

  String AUTH_LDAP_SERVER_SSL_CERT_TRUST_PATH = "auth.ldap.server.ssl.cert.trust-path";
  String AUTH_LDAP_SERVER_SSL_CERT_AUTH_PATH = "auth.ldap.server.ssl.cert.auth-path";
  String AUTH_LDAP_SERVER_SSL_CERT_KEY_PATH = "auth.ldap.server.ssl.cert.key-path";

  String FEATURE_PREFIX = "features.";
}
