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
