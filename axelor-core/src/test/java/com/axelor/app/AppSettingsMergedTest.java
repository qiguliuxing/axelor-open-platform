/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.axelor.TestingHelpers;
import com.axelor.common.ClassUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

public class AppSettingsMergedTest {

  @BeforeAll
  static void setup() {
    TestingHelpers.resetSettings();
  }

  /** Properties merged from internal file + ext file + env + system prop */
  @Nested
  class InternalFileMergedSettingsTest {

    @AfterEach
    void tearDown() {
      TestingHelpers.resetSettings();
    }

    @Test
    public void testMergedSettings() {
      AppSettings setting = AppSettings.get();

      assertEquals(22, setting.getInternalProperties().size());
      assertEquals("3", setting.get("quartz.thread-count"));
      assertEquals("Tests", setting.get("application.name"));

      // should not be found
      assertNull(setting.get("var"));
    }
  }

  /** Properties merged from internal file + ext file + env + system prop */
  @Nested
  class WithExternalMergedSettingsTest {

    @BeforeEach
    void setup() {
      loadExtFile();
    }

    @AfterEach
    void tearDown() {
      TestingHelpers.resetSettings();
    }

    @Test
    public void testMergedSettings() {
      AppSettings setting = AppSettings.get();

      assertEquals(24, setting.getInternalProperties().size());
      assertEquals("3", setting.get("quartz.thread-count"));

      // external config should get preference
      assertEquals("From ext yml", setting.get("application.name"));

      // from external file only
      assertEquals("Bar", setting.get("views.some-bar"));

      // should not be found
      assertNull(setting.get("var"));
    }
  }

  /** Properties merged from internal file + ext file + env + system prop */
  @Nested
  @ExtendWith(MyEnv.class)
  class FullMergedSettingsTest {

    @BeforeEach
    void setup() {
      loadExtFile();
      loadSystemProps();
    }

    @AfterEach
    void tearDown() {
      TestingHelpers.resetSettings();
    }

    @Test
    public void testMergedSettings() {
      AppSettings setting = AppSettings.get();

      // system prop should get preference
      assertEquals("mySystemEnv", setting.get("my.env"));

      // external config should get preference
      assertEquals("From ext yml", setting.get("application.name"));

      // should not be overridden
      assertEquals("3", setting.get("quartz.thread-count"));

      // from env only
      assertEquals("true", setting.get("var"));
    }
  }

  static class MyEnv extends EnvironmentVariablesExtension {
    public MyEnv() {
      super();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
      super.beforeAll(context);
      set("AXELOR_CONFIG_MY_ENV", "myEnv");
      set("AXELOR_CONFIG_VAR", "true");
    }
  }

  protected static void loadSystemProps() {
    System.setProperty("axelor.config.my.env", "mySystemEnv");
  }

  protected static void loadExtFile() {
    String file = ClassUtils.getResource("configs/ext-config.yml").getFile();
    System.setProperty("axelor.config", file);

    System.setProperty("axelor.config.my.env", "mySystemEnv");
  }
}
