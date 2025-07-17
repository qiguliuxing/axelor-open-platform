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
package com.axelor.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.axelor.rpc.Request;
import com.axelor.rpc.Response;
import com.google.common.collect.ImmutableMap;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.client.Invocation;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class ResourceTest extends AbstractTest {

  protected String model = "com.axelor.web.db.Contact";

  protected Invocation.Builder crud(String action) {
    String path = "/rest/" + model;
    if (action != null) {
      path = path + "/" + action;
    }
    return jsonPath(path);
  }

  @Test
  public void testFields() {

    Response response = jsonPath("/meta/fields/" + model).get(Response.class);

    assertNotNull(response);
    assertNotNull(response.getData());

    assertTrue(response.getData() instanceof Map);

    assertEquals(((Map<?, ?>) response.getData()).get("model"), model);
  }

  @Test
  public void testSearch() {

    Request request = new Request();
    request.setData(ImmutableMap.of("firstName", (Object) "John", "lastName", "Teen"));

    Response response = crud("search").post(Entity.json(request), Response.class);

    assertNotNull(response);
    assertNotNull(response.getData());

    assertTrue(response.getData() instanceof List);
    assertTrue(((List<?>) response.getData()).size() > 0);
  }
}
