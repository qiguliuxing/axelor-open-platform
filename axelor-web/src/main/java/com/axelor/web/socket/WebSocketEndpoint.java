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
package com.axelor.web.socket;

import com.axelor.common.StringUtils;
import com.axelor.web.socket.inject.WebSocketConfigurator;
import com.axelor.web.socket.inject.WebSocketSecurity;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@WebSocketSecurity
@ServerEndpoint(
    value = "/websocket",
    decoders = MessageDecoder.class,
    encoders = MessageEncoder.class,
    configurator = WebSocketConfigurator.class)
public class WebSocketEndpoint {

  private static final Map<String, Channel> CHANNELS = new ConcurrentHashMap<>();

  private static final Logger log = LoggerFactory.getLogger(WebSocketEndpoint.class);

  @Inject
  public WebSocketEndpoint(Set<Channel> channels) {
    channels.stream().filter(Channel::isEnabled).forEach(this::register);
  }

  private void register(Channel channel) {
    String name = channel.getName();
    if (StringUtils.isBlank(name)) {
      throw new IllegalArgumentException(
          "Channel must have a name: " + channel.getClass().getName());
    }
    if (CHANNELS.containsKey(name)) {
      throw new IllegalStateException("Duplicate channel found: " + name);
    }
    log.info("Registering channel: {}", name);
    CHANNELS.put(name, channel);
  }

  @OnOpen
  public void onOpen(Session session, EndpointConfig config) {
    // Nothing to do
  }

  @OnClose
  public void onClose(Session session, CloseReason reason) {
    CHANNELS.values().forEach(ch -> ch.onUnsubscribe(session));
  }

  @OnError
  public void onError(Session session, Throwable thr) {
    // Client sent a message, but the session has already closed.
    if (session != null && !session.isOpen()) {
      log.trace(thr.getMessage(), thr);
      return;
    }
    if (thr instanceof IOException) {
      // usually indicates a disconnected client
      log.trace(thr.getMessage(), thr);
    } else {
      log.error("Websocket error", thr);
    }
  }

  @OnMessage
  public void onMessage(Session session, Message message) {
    Channel channel = CHANNELS.get(message.getChannel());
    if (channel == null) {
      return;
    }
    switch (message.getType()) {
      case SUB:
        channel.onSubscribe(session);
        break;
      case UNS:
        channel.onUnsubscribe(session);
        break;
      case MSG:
        channel.onMessage(session, message);
        break;
    }
  }
}
