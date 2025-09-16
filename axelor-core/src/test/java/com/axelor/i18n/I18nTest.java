/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.i18n;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axelor.JpaTest;
import com.axelor.meta.db.MetaTranslation;
import com.axelor.meta.db.repo.MetaTranslationRepository;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class I18nTest extends JpaTest {

  @Inject private MetaTranslationRepository translations;

  @BeforeEach
  @Transactional
  public void setup() {
    if (translations.all().count() > 0) {
      return;
    }
    MetaTranslation obj;

    obj = new MetaTranslation();
    obj.setKey("Hello World!!!");
    obj.setMessage("Hello...");
    obj.setLanguage("en");
    obj = translations.save(obj);

    obj = new MetaTranslation();
    obj.setKey("{0} record selected.");
    obj.setMessage("{0} record selected.");
    obj.setLanguage("en");
    obj = translations.save(obj);

    obj = new MetaTranslation();
    obj.setKey("{0} records selected.");
    obj.setMessage("{0} records selected.");
    obj.setLanguage("en");
    obj = translations.save(obj);

    I18nBundle.invalidate();
  }

  @Test
  public void test() {

    // test simple
    assertEquals("Hello...", I18n.get("Hello World!!!"));

    // test plural
    assertEquals(
        "1 record selected.", I18n.get("{0} record selected.", "{0} records selected.", 1));
    assertEquals(
        "5 records selected.", I18n.get("{0} record selected.", "{0} records selected.", 5));
  }
}
