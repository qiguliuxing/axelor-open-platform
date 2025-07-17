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
package com.axelor.test;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;
import com.google.inject.servlet.GuiceServletContextListener;
import com.google.inject.servlet.ServletModule;
import dev.resteasy.guice.GuiceResteasyBootstrapServletContextListener;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.DeploymentManager;
import io.undertow.servlet.api.InstanceFactory;
import io.undertow.servlet.api.InstanceHandle;
import io.undertow.servlet.api.ListenerInfo;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import java.util.ArrayList;
import java.util.List;
import org.jboss.resteasy.plugins.server.servlet.HttpServletDispatcher;

public final class WebServer {

  private static final int PORT = 9997;
  private static final String CONTEXT_PATH = "/";

  private Undertow server;

  private Injector injector;

  private List<Module> modules;

  private WebServer() {
    this.modules = new ArrayList<>();
  }

  public static WebServer create(Module... modules) {
    final WebServer server = new WebServer();
    for (Module module : modules) {
      server.modules.add(module);
    }
    return server;
  }

  public void start() {
    if (server == null) {
      server = createServer();
    }
    server.start();
  }

  public void stop() {
    if (server != null) {
      server.stop();
    }
  }

  public Client client() {
    return ClientBuilder.newClient();
  }

  public WebTarget target() {
    return client().target("http://localhost:%s%s".formatted(PORT, CONTEXT_PATH));
  }

  private Undertow createServer() {

    final DeploymentInfo builder = Servlets.deployment();

    builder.setClassLoader(Thread.currentThread().getContextClassLoader());
    builder.setDeploymentName("test.war");
    builder.setContextPath(CONTEXT_PATH);

    builder.addFilter(Servlets.filter("guiceFilter", GuiceFilter.class));
    builder.addFilterUrlMapping("guiceFilter", "/*", DispatcherType.REQUEST);

    builder.addListener(
        new ListenerInfo(GuiceServletContextListener.class, new GuiceListenerFactory(this)));

    builder.addListener(
        new ListenerInfo(
            GuiceResteasyBootstrapServletContextListener.class, new ResteasyListenerFactory(this)));

    final DeploymentManager manager = Servlets.defaultContainer().addDeployment(builder);
    manager.deploy();

    try {
      final PathHandler root = new PathHandler().addPrefixPath(CONTEXT_PATH, manager.start());
      return Undertow.builder().addHttpListener(PORT, "localhost").setHandler(root).build();
    } catch (ServletException e) {
      throw new RuntimeException(e);
    }
  }

  static class ResteasyListenerFactory
      implements InstanceFactory<GuiceResteasyBootstrapServletContextListener> {

    private WebServer server;

    public ResteasyListenerFactory(WebServer server) {
      this.server = server;
    }

    @Override
    public InstanceHandle<GuiceResteasyBootstrapServletContextListener> createInstance()
        throws InstantiationException {

      return new InstanceHandle<>() {

        @Override
        public GuiceResteasyBootstrapServletContextListener getInstance() {
          if (server.injector == null) {
            throw new RuntimeException("Guice injector is not initialized.");
          }
          return server.injector.getInstance(GuiceResteasyBootstrapServletContextListener.class);
        }

        @Override
        public void release() {}
      };
    }
  }

  static class GuiceListenerFactory implements InstanceFactory<GuiceServletContextListener> {

    private WebServer server;

    public GuiceListenerFactory(WebServer server) {
      this.server = server;
    }

    @Override
    public InstanceHandle<GuiceServletContextListener> createInstance()
        throws InstantiationException {
      return new InstanceHandle<>() {

        @Override
        public GuiceServletContextListener getInstance() {
          return new GuiceServletContextListener() {

            @Override
            protected Injector getInjector() {
              if (server.injector == null) {
                server.injector =
                    Guice.createInjector(
                        new ServletModule() {
                          @Override
                          protected void configureServlets() {
                            for (Module module : server.modules) {
                              install(module);
                            }
                            bind(HttpServletDispatcher.class).asEagerSingleton();
                            serve("/*").with(HttpServletDispatcher.class);
                          }
                        });
              }
              return server.injector;
            }
          };
        }

        @Override
        public void release() {}
      };
    }
  }
}
