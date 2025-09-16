/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.inject.logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.axelor.test.GuiceExtension;
import com.axelor.test.GuiceModules;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;

@ExtendWith(GuiceExtension.class)
@GuiceModules({LoggerModule.class})
public class TestLogger {

  private Logger log1;

  @Inject private Logger log2;

  private Logger log3;

  @Inject private TestLoggerService service;

  @Inject
  public TestLogger(Logger log1) {
    this.log1 = log1;
  }

  @Inject
  public void setLog3(Logger log3) {
    this.log3 = log3;
  }

  @Test
  public void testContructorInject() {
    assertNotNull(log1);
    assertEquals(TestLogger.class.getName(), log1.getName());
  }

  @Test
  public void testMemberInject() {
    assertNotNull(log2);
    assertEquals(TestLogger.class.getName(), log2.getName());
  }

  @Test
  public void testSetterInject() {
    assertNotNull(log3);
    assertEquals(TestLogger.class.getName(), log3.getName());
  }

  @Test
  public void testServiceInject() {
    assertNotNull(service);
    assertNotNull(service.getLog());
    assertEquals(TestLoggerService.class.getName(), service.getLog().getName());
  }
}
