/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.tomcat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.Context;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.realm.MemoryRealm;
import org.apache.catalina.startup.ContextConfig;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.catalina.webresources.FileResourceSet;
import org.apache.catalina.webresources.StandardRoot;

public class TomcatServer {

  private Tomcat tomcat;

  private final TomcatOptions options;

  public TomcatServer(TomcatOptions options) {
    this.options = options;
  }

  private void copyConf(String name, Path confDir) throws IOException {
    final Path dest = confDir.resolve(name);
    if (Files.notExists(dest)) {
      try (InputStream is = getClass().getResourceAsStream("/conf/" + name)) {
        Files.copy(is, dest);
      }
    }
  }

  private Tomcat create() throws IOException {
    final Path baseDir = options.getBaseDir();
    final Path confDir = baseDir.resolve("conf");
    final Path logsDir = baseDir.resolve("logs");

    Files.createDirectories(confDir);
    Files.createDirectories(logsDir);

    copyConf("web.xml", confDir);
    copyConf("tomcat-users.xml", confDir);
    copyConf("logging.properties", confDir);

    final int port = options.getPort();
    final String contextPath = options.getContextPath();
    final String docBase = options.getDocBase().toFile().getAbsolutePath();

    System.setProperty("java.util.logging.manager", "org.apache.juli.ClassLoaderLogManager");
    System.setProperty(
        "java.util.logging.config.file", confDir.resolve("logging.properties").toString());

    final Tomcat tomcat =
        new Tomcat() {

          @Override
          public Context addWebapp(Host host, String contextPath, String docBase) {
            final ContextConfig config = new ContextConfig();
            final Context context = new StandardContext();

            context.setPath(contextPath);
            context.setDocBase(docBase);
            context.addLifecycleListener(config);
            config.setDefaultWebXml(confDir.resolve("web.xml").toFile().getAbsolutePath());

            if (host == null) {
              getHost().addChild(context);
            } else {
              host.addChild(context);
            }

            return context;
          }
        };

    tomcat.setBaseDir(baseDir.toFile().getAbsolutePath());
    tomcat.getHost().setAutoDeploy(false);

    final MemoryRealm memoryRealm = new MemoryRealm();
    tomcat.getEngine().setRealm(memoryRealm);

    final Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    connector.setPort(port);
    connector.setProperty("bindOnInit", "false");

    tomcat.setConnector(connector);
    tomcat.setPort(port);

    final WebappLoader loader = new WebappLoader();
    final StandardContext context = (StandardContext) tomcat.addWebapp(contextPath, docBase);
    final StandardRoot resources = new StandardRoot();

    context.setLoader(loader);
    context.setParentClassLoader(getClass().getClassLoader());
    context.setResources(resources);
    context.setUnpackWAR(false);

    // additional webapp resources
    options.getExtraResources().stream()
        .map(Path::toFile)
        .map(dir -> new DirResourceSet(resources, "/", dir.getAbsolutePath(), "/"))
        .forEach(resources::addPostResources);

    // additional classes, should be search before libs
    options.getClasses().stream()
        .map(Path::toFile)
        .map(dir -> new DirResourceSet(resources, "/WEB-INF/classes", dir.getAbsolutePath(), "/"))
        .forEach(resources::addPreResources);

    // additional libs
    options.getLibs().stream()
        .map(Path::toFile)
        .map(
            file ->
                new FileResourceSet(
                    resources, "/WEB-INF/lib/" + file.getName(), file.getAbsolutePath(), "/"))
        .forEach(resources::addPostResources);

    // Skip scanning
    TldSkipPatterns.apply(context.getJarScanner());

    tomcat
        .getServer()
        .addLifecycleListener(
            new LifecycleListener() {
              @Override
              public void lifecycleEvent(LifecycleEvent event) {
                final Lifecycle lifecycle = event.getLifecycle();
                final boolean existAfterStart = System.getProperty("axelor.task.database") != null;
                if (lifecycle.getState() == LifecycleState.STARTED) {
                  tomcat.getServer().removeLifecycleListener(this);
                  if (existAfterStart) {
                    stop();
                  } else {
                    System.out.println();
                    System.out.println("Running at http://localhost:" + port + contextPath);
                    System.out.println();
                  }
                }
              }
            });

    Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

    return tomcat;
  }

  public void start() {
    final String catalinaBase = System.getProperty("catalina.base");
    try {
      System.setProperty("catalina.base", options.getBaseDir().toFile().getAbsolutePath());
      if (tomcat == null) {
        tomcat = create();
      }
      tomcat.start();
    } catch (Exception e) {
      throw new RuntimeException("Cannot start Tomcat " + e.getMessage(), e);
    } finally {
      if (catalinaBase != null) {
        System.setProperty("catalina.base", catalinaBase);
      }
    }
    if (tomcat != null) {
      tomcat.getServer().await();
    }
  }

  public void stop() {
    if (tomcat == null) {
      return;
    }
    try {
      tomcat.stop();
      tomcat = null;
    } catch (Exception e) {
      throw new RuntimeException("Cannot Stop Tomcat " + e.getMessage(), e);
    }
  }
}
