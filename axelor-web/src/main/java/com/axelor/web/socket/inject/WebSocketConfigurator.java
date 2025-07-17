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
package com.axelor.web.socket.inject;

import com.axelor.db.tenants.TenantResolver;
import com.axelor.inject.Beans;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.servlet.http.HttpSession;
import jakarta.websocket.Extension;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerEndpointConfig;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.subject.Subject;

public class WebSocketConfigurator extends ServerEndpointConfig.Configurator {

  static final String TENANT_ID = "tenant-id";
  static final String TENANT_HOST = "tenant-host";

  @Override
  public <T> T getEndpointInstance(Class<T> endpointClass) throws InstantiationException {
    return Beans.get(endpointClass);
  }

  @Override
  public String getNegotiatedSubprotocol(List<String> supported, List<String> requested) {
    return requested.stream().filter(supported::contains).findFirst().orElse("");
  }

  @Override
  public List<Extension> getNegotiatedExtensions(
      List<Extension> installed, List<Extension> requested) {
    return requested.stream()
        .filter(e -> installed.stream().anyMatch(x -> Objects.equals(x.getName(), e.getName())))
        .collect(Collectors.toList());
  }

  @Override
  public boolean checkOrigin(String originHeaderValue) {
    return true;
  }

  @Override
  public void modifyHandshake(
      ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
    HttpSession httpSession = (HttpSession) request.getHttpSession();
    final Map<String, Object> properties = sec.getUserProperties();
    properties.put(HttpSession.class.getName(), httpSession);
    properties.put(Subject.class.getName(), SecurityUtils.getSubject());
    properties.put(SecurityManager.class.getName(), SecurityUtils.getSecurityManager());

    final String tenantId = TenantResolver.currentTenantIdentifier();
    if (tenantId != null) {
      properties.put(TENANT_ID, tenantId);
    }
    final String tenantHost = TenantResolver.currentTenantHost();
    if (tenantHost != null) {
      properties.put(TENANT_HOST, tenantHost);
    }
  }
}
