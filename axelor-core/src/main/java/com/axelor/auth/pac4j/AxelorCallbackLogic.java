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
package com.axelor.auth.pac4j;

import com.axelor.common.StringUtils;
import com.axelor.common.UriBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import org.pac4j.core.client.BaseClient;
import org.pac4j.core.client.finder.DefaultCallbackClientFinder;
import org.pac4j.core.config.Config;
import org.pac4j.core.context.CallContext;
import org.pac4j.core.context.FrameworkParameters;
import org.pac4j.core.context.WebContext;
import org.pac4j.core.engine.DefaultCallbackLogic;
import org.pac4j.core.exception.http.HttpAction;
import org.pac4j.core.exception.http.OkAction;
import org.pac4j.core.exception.http.WithLocationAction;
import org.pac4j.core.http.adapter.HttpActionAdapter;
import org.pac4j.core.util.HttpActionHelper;
import org.pac4j.core.util.Pac4jConstants;
import org.pac4j.jee.context.JEEFrameworkParameters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AxelorCallbackLogic extends DefaultCallbackLogic {

  private final ErrorHandler errorHandler;
  private final AxelorCsrfMatcher csrfMatcher;
  private final AuthPac4jInfo pac4jInfo;

  private static final Logger logger =
      LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Inject
  public AxelorCallbackLogic(
      ErrorHandler errorHandler,
      AxelorCsrfMatcher csrfMatcher,
      DefaultCallbackClientFinder clientFinder,
      AuthPac4jInfo pac4jInfo) {
    this.errorHandler = errorHandler;
    this.csrfMatcher = csrfMatcher;
    this.pac4jInfo = pac4jInfo;
    setClientFinder(clientFinder);
  }

  @Override
  public Object perform(
      Config config,
      String inputDefaultUrl,
      Boolean inputRenewSession,
      String defaultClient,
      FrameworkParameters parameters) {

    final var jeeParameters = (JEEFrameworkParameters) parameters;

    try {
      jeeParameters.getRequest().setCharacterEncoding("UTF-8");
    } catch (UnsupportedEncodingException e) {
      final var context = config.getWebContextFactory().newContext(parameters);
      return handleException(e, config.getHttpActionAdapter(), context);
    }

    return super.perform(
        config, pac4jInfo.getBaseUrl(), inputRenewSession, defaultClient, parameters);
  }

  /**
   * Redefined to account for failed clients
   *
   * @see com.axelor.auth.pac4j.AxelorCallbackClientFinder
   */
  @Override
  protected void renewSession(CallContext ctx, Config config) {
    final var context = ctx.webContext();
    final var sessionStore = ctx.sessionStore();

    final var optOldSessionId = sessionStore.getSessionId(context, true);
    if (optOldSessionId.isEmpty()) {
      logger.error(
          "No old session identifier retrieved although the session creation has been requested");
    } else {
      final var oldSessionId = optOldSessionId.get();
      final var renewed = sessionStore.renewSession(context);
      if (renewed) {
        final var optNewSessionId = sessionStore.getSessionId(context, true);
        if (optNewSessionId.isEmpty()) {
          logger.error(
              "No new session identifier retrieved although the session creation has been requested");
        } else {
          final var newSessionId = optNewSessionId.get();
          logger.debug("Renewing session: {} -> {}", oldSessionId, newSessionId);
          final var clients = config.getClients();
          if (clients != null) {
            final var clientList = clients.getClients();
            for (final var client : clientList) {
              final var baseClient = (BaseClient) client;

              // Don't fail because of any unavailable clients.
              if (baseClient.isInitialized()) {
                try {
                  baseClient.notifySessionRenewal(ctx, oldSessionId);
                } catch (Exception e) {
                  logger.error(e.getMessage(), e);
                }
              }
            }
          }
        }
      } else {
        logger.error("Unable to renew the session. The session store may not support this feature");
      }
    }
  }

  @Override
  protected HttpAction redirectToOriginallyRequestedUrl(CallContext ctx, String defaultUrl) {
    // Add CSRF token cookie and header
    csrfMatcher.addResponseCookieAndHeader(ctx);

    // If XHR, return status code only
    if (AuthPac4jInfo.isXHR(ctx)) {
      return new OkAction("{}");
    }

    final var context = ctx.webContext();
    final var sessionStore = ctx.sessionStore();

    final String requestedUrl =
        sessionStore
            .get(context, Pac4jConstants.REQUESTED_URL)
            .filter(WithLocationAction.class::isInstance)
            .map(action -> ((WithLocationAction) action).getLocation())
            .orElse("");

    String redirectUrl = defaultUrl;
    if (StringUtils.notBlank(requestedUrl)) {
      sessionStore.set(context, Pac4jConstants.REQUESTED_URL, null);
      redirectUrl = requestedUrl;
    }

    final String hashLocation =
        context
            .getRequestParameter(AxelorSecurityLogic.HASH_LOCATION_PARAMETER)
            .or(
                () ->
                    sessionStore
                        .get(context, AxelorSecurityLogic.HASH_LOCATION_PARAMETER)
                        .map(String::valueOf))
            .orElse("");
    if (StringUtils.notBlank(hashLocation)) {
      redirectUrl = UriBuilder.from(redirectUrl).setFragment(hashLocation).toUri().toString();
    }

    logger.debug("redirectUrl: {}", redirectUrl);
    return HttpActionHelper.buildRedirectUrlAction(context, redirectUrl);
  }

  @Override
  protected Object handleException(
      Exception e, HttpActionAdapter httpActionAdapter, WebContext context) {
    return errorHandler.handleException(e, httpActionAdapter, context);
  }
}
