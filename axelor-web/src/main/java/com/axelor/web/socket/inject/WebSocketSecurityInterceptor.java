/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.web.socket.inject;

import com.axelor.db.tenants.TenantResolver;
import com.axelor.inject.Beans;
import com.google.inject.persist.UnitOfWork;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.session.InvalidSessionException;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketSecurityInterceptor implements MethodInterceptor {

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private <T> T withAuth(Session session, Function<Subject, T> task) {
    final Map<String, Object> properties = session.getUserProperties();
    final Object manager = properties.get(SecurityManager.class.getName());
    final Object subject = properties.get(Subject.class.getName());

    final String tenantId = (String) properties.get(WebSocketConfigurator.TENANT_ID);
    final String tenantHost = (String) properties.get(WebSocketConfigurator.TENANT_HOST);
    TenantResolver.setCurrentTenant(tenantId, tenantHost);

    final UnitOfWork unitOfWork = Beans.get(UnitOfWork.class);
    try {
      unitOfWork.begin();
    } catch (IllegalStateException e) {
      // Ignore
    }
    try {
      ThreadContext.bind((SecurityManager) manager);
      ThreadContext.bind((Subject) subject);
      return task.apply((Subject) subject);
    } finally {
      ThreadContext.remove();
      unitOfWork.end();
    }
  }

  @Override
  public Object invoke(MethodInvocation invocation) throws Throwable {
    Method method = invocation.getMethod();
    if (notHandler(method)) {
      return invocation.proceed();
    }

    final Session session =
        List.of(invocation.getArguments()).stream()
            .filter(Session.class::isInstance)
            .map(Session.class::cast)
            .findFirst()
            .orElse(null);

    if (session == null) {
      return invocation.proceed();
    }

    if (isMessageHandler(method)) {
      return withAuth(
          session,
          subject -> {
            boolean authenticated;
            try {
              authenticated = subject.isAuthenticated();
            } catch (InvalidSessionException e) {
              // Session removed because of timeout
              authenticated = false;
              logger.error(e.getMessage());
            }
            if (authenticated) {
              try {
                return invocation.proceed();
              } catch (Throwable e) {
                throw new RuntimeException(e);
              }
            }
            try {
              session.close();
            } catch (IOException e) {
              // ignore
            }
            return null;
          });
    }

    return withAuth(
        session,
        subject -> {
          try {
            return invocation.proceed();
          } catch (Throwable e) {
            throw new RuntimeException(e);
          }
        });
  }

  private static boolean notHandler(Method method) {
    return method.getAnnotation(OnOpen.class) == null
        && method.getAnnotation(OnClose.class) == null
        && method.getAnnotation(OnError.class) == null
        && method.getAnnotation(OnMessage.class) == null;
  }

  private static boolean isMessageHandler(Method method) {
    return method.getAnnotation(OnMessage.class) != null;
  }
}
