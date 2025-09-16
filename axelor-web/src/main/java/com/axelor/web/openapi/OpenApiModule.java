/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.web.openapi;

import com.axelor.app.AppSettings;
import com.axelor.app.AvailableAppSettings;
import com.axelor.common.StringUtils;
import com.google.inject.AbstractModule;
import io.swagger.v3.jaxrs2.integration.JaxrsOpenApiContextBuilder;
import io.swagger.v3.jaxrs2.integration.resources.AcceptHeaderOpenApiResource;
import io.swagger.v3.jaxrs2.integration.resources.OpenApiResource;
import io.swagger.v3.oas.integration.OpenApiConfigurationException;
import io.swagger.v3.oas.integration.SwaggerConfiguration;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OpenApiModule extends AbstractModule {

  private static Logger log = LoggerFactory.getLogger(OpenApiModule.class);

  @Override
  @SuppressWarnings("rawtypes")
  protected void configure() {

    log.debug("Configuring OpenAPI...");

    bind(AcceptHeaderOpenApiResource.class);
    bind(OpenApiResource.class);

    Info info =
        new Info()
            .title(AppSettings.get().get(AvailableAppSettings.APPLICATION_NAME))
            .description(AppSettings.get().get(AvailableAppSettings.APPLICATION_DESCRIPTION))
            .version(AppSettings.get().get(AvailableAppSettings.APPLICATION_VERSION));

    OpenAPI openAPI = new OpenAPI();
    openAPI.info(info);
    openAPI.addServersItem(new Server().url(getServerUrl()));

    SwaggerConfiguration swaggerConfiguration =
        new SwaggerConfiguration()
            .scannerClass(AxelorOpenApiScanner.class.getName())
            .openAPI(openAPI);

    try {
      new JaxrsOpenApiContextBuilder()
          .openApiConfiguration(swaggerConfiguration)
          .buildContext(true);
    } catch (OpenApiConfigurationException e) {
      log.error(e.toString());
    }
  }

  private String getServerUrl() {
    final AppSettings settings = AppSettings.get();
    final String baseUrl = settings.getBaseURL();
    if (StringUtils.notBlank(baseUrl)) {
      return baseUrl + "/ws";
    }
    return ".";
  }
}
