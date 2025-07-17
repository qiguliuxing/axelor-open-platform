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
package com.axelor.meta.loader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axelor.common.ResourceUtils;
import com.axelor.db.Query.Selector;
import com.axelor.meta.MetaTest;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.views.AbstractView;
import com.axelor.meta.schema.views.ChartView;
import com.axelor.meta.schema.views.FormView;
import com.axelor.meta.schema.views.PanelInclude;
import com.axelor.meta.schema.views.Search;
import com.axelor.script.ScriptHelper;
import com.axelor.test.db.Title;
import com.google.common.collect.Maps;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import java.io.StringWriter;
import java.net.URL;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class TestViews extends MetaTest {

  @Inject private ViewLoader loader;

  @Test
  public void test1() throws Exception {
    ObjectViews views = this.unmarshal("com/axelor/meta/Contact.xml", ObjectViews.class);

    assertNotNull(views);
    assertNotNull(views.getViews());
    assertEquals(2, views.getViews().size());

    String json = toJson(views);

    assertNotNull(json);
  }

  @Test
  public void test2() throws Exception {
    ObjectViews views = this.unmarshal("com/axelor/meta/Welcome.xml", ObjectViews.class);

    assertNotNull(views);
    assertNotNull(views.getViews());
    assertEquals(1, views.getViews().size());

    String json = toJson(views);

    assertNotNull(json);
  }

  @Test
  @Transactional
  public void test3() throws Exception {
    ObjectViews views = this.unmarshal("com/axelor/meta/Search.xml", ObjectViews.class);
    assertNotNull(views);
    assertNotNull(views.getViews());
    assertEquals(1, views.getViews().size());

    String json = toJson(views);

    assertNotNull(json);

    Search search = (Search) views.getViews().getFirst();

    Title title = all(Title.class).filter("self.code = ?", "mr").fetchOne();
    assertNotNull(title);

    Map<String, Object> binding = Maps.newHashMap();
    binding.put("customer", "Some");
    binding.put("date", "2011-11-11");
    binding.put("xxx", 111);
    binding.put("title", title);
    binding.put("country", "IN");
    binding.put("value", "100.10");

    Map<String, Object> partner = Maps.newHashMap();
    partner.put("firstName", "Name");

    binding.put("partner", partner);

    ScriptHelper helper = search.scriptHandler(binding);

    for (Search.SearchSelect s : search.getSelects()) {
      Selector q = s.toQuery(helper);
      if (q == null) continue;

      assertNotNull(q.fetch(search.getLimit(), 0));
    }
  }

  @Test
  @Transactional
  public void testInclude() throws Exception {

    final URL url = ResourceUtils.getResource("com/axelor/meta/Include.xml");
    loader.process(url, new Module("test"), false);

    final AbstractView form1 = XMLViews.findView("contact-form1", null, null, "test");
    final AbstractView form2 = XMLViews.findView("contact-form2", null, null, "test");

    assertTrue(form1 instanceof FormView);
    assertTrue(form2 instanceof FormView);

    final PanelInclude include = (PanelInclude) ((FormView) form2).getItems().getFirst();
    final AbstractView included = include.getView();

    assertEquals(form1.getName(), included.getName());
  }

  @Test
  public void testChart() throws Exception {
    ObjectViews views = this.unmarshal("com/axelor/meta/Charts.xml", ObjectViews.class);

    ChartView chartView = (ChartView) views.getViews().getFirst();

    assertEquals(1, chartView.getActions().size());
    assertEquals("testChartAction", chartView.getActions().getFirst().getName());
    assertEquals(
        "com.axelor.meta.web.Hello:chartAction", chartView.getActions().getFirst().getAction());

    StringWriter writer = new StringWriter();
    XMLViews.marshal(views, writer);

    assertTrue(writer.toString().contains("com.axelor.meta.web.Hello:chartAction"));
  }
}
