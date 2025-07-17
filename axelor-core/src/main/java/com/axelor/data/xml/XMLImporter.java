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
package com.axelor.data.xml;

import com.axelor.data.ImportException;
import com.axelor.data.ImportTask;
import com.axelor.data.Importer;
import com.axelor.data.Listener;
import com.axelor.data.XStreamUtils;
import com.axelor.data.adapter.DataAdapter;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.internal.DBHelper;
import com.google.common.base.Preconditions;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.WstxDriver;
import com.thoughtworks.xstream.mapper.MapperWrapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.FlushModeType;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.xml.stream.XMLInputFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * XML data importer. <br>
 * <br>
 * This class also provides {@link #run(ImportTask)} method to import data programmatically. <br>
 * <br>
 * For example:
 *
 * <pre>
 * XMLImporter importer = new XMLImporter(&quot;path/to/xml-config.xml&quot;);
 *
 * importer.runTask(new ImportTask(){
 *
 * 	public void configure() throws IOException {
 * 		input(&quot;contacts.xml&quot;, new File(&quot;data/xml/contacts.xml&quot;));
 * 		input(&quot;contacts.xml&quot;, new File(&quot;data/xml/contacts2.xml&quot;));
 * 	}
 *
 * 	public boolean handle(ImportException e) {
 * 		System.err.println("Import error: " + e);
 * 		return true;
 * 	}
 * }
 * </pre>
 */
public class XMLImporter implements Importer {

  private Logger log = LoggerFactory.getLogger(getClass());

  private File dataDir;

  private XMLConfig config;

  private Map<String, Object> context;

  private List<Listener> listeners = new ArrayList<>();

  private boolean canClear = true;

  @Inject
  public XMLImporter(
      @Named("axelor.data.config") String configFile, @Named("axelor.data.dir") String dataDir) {

    Objects.requireNonNull(configFile);

    File file = new File(configFile);

    Preconditions.checkArgument(file.isFile(), "No such file: " + configFile);

    if (dataDir != null) {
      File _data = new File(dataDir);
      Preconditions.checkArgument(_data.isDirectory());
      this.dataDir = _data;
    }

    this.config = XMLConfig.parse(file);
  }

  public XMLImporter(String configFile) {
    this(configFile, null);
  }

  public void setContext(Map<String, Object> context) {
    this.context = context;
  }

  private List<File> getFiles(String... names) {
    List<File> all = new ArrayList<>();
    for (String name : names) all.add(dataDir != null ? new File(dataDir, name) : new File(name));
    return all;
  }

  public void addListener(Listener listener) {
    this.listeners.add(listener);
  }

  public void clearListener() {
    this.listeners.clear();
  }

  public void setCanClear(boolean canClear) {
    this.canClear = canClear;
  }

  @Override
  public void run() {

    for (XMLInput input : config.getInputs()) {

      String fileName = input.getFileName();
      List<File> files = this.getFiles(fileName);

      for (File file : files) {
        try {
          this.process(input, file);
        } catch (Exception e) {
          log.error("Error while importing {}.", file, e);
        }
      }
    }
  }

  public void run(ImportTask task) {
    try {
      task.init();
      for (XMLInput input : config.getInputs()) {
        for (Reader reader : task.getReader(input.getFileName())) {
          try {
            process(input, reader);
          } catch (ImportException e) {
            if (!task.handle(e)) {
              break;
            }
          }
        }
      }
    } catch (IOException e) {
      throw new IllegalArgumentException(e);
    } finally {
      task.close();
    }
  }

  /**
   * Process the data file with the given input binding.
   *
   * @param input input binding configuration
   * @param file data file
   * @throws ImportException
   */
  private void process(XMLInput input, File file) throws ImportException {
    try (final Reader reader =
        new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
      log.info("Importing: {}", file.getName());
      this.process(input, reader);
    } catch (IOException e) {
      throw new ImportException(e);
    }
  }

  private void process(XMLInput input, Reader reader) throws ImportException {

    final int batchSize = DBHelper.getJdbcBatchSize();

    final WstxDriver driver =
        new WstxDriver() {
          @Override
          protected XMLInputFactory createInputFactory() {
            XMLInputFactory factory = super.createInputFactory();
            factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);
            return factory;
          }
        };

    final XStream stream =
        new XStream(driver) {

          private String root = null;

          @Override
          @SuppressWarnings("all")
          protected MapperWrapper wrapMapper(MapperWrapper next) {

            return new MapperWrapper(next) {

              @Override
              public Class realClass(String elementName) {
                if (root == null) {
                  root = elementName;
                  return Document.class;
                }
                return Element.class;
              }
            };
          }
        };

    final Map<String, Object> context = new HashMap<>();

    // Put global context
    if (this.context != null) {
      context.putAll(this.context);
    }

    // Put data path in context
    if (dataDir != null) {
      context.put("__path__", dataDir.toPath());
    }

    final XMLBinder binder =
        new XMLBinder(input, context) {

          int count = 0;
          int total = 0;

          @Override
          protected void handle(Object bean, XMLBind binding, Map<String, Object> ctx) {
            if (bean == null) {
              return;
            }
            try {
              bean = binding.call(bean, ctx);
              if (bean != null) {
                bean = JPA.manage((Model) bean);
                count++;
                for (Listener listener : listeners) {
                  listener.imported((Model) bean);
                }
              }
            } catch (Exception e) {
              log.error("Unable to import object {}.", bean);
              log.error("With binding {}.", binding);
              log.error("With exception:", e);

              // Recover the transaction
              if (JPA.em().getTransaction().getRollbackOnly()) {
                JPA.em().getTransaction().rollback();
              }
              if (!JPA.em().getTransaction().isActive()) {
                JPA.em().getTransaction().begin();
              }

              for (Listener listener : listeners) {
                listener.handle((Model) bean, e);
              }
            }
            if (++total % batchSize == 0) {
              JPA.flush();
              JPA.clear();
            }
          }

          @Override
          protected void finish() {
            for (Listener listener : listeners) {
              listener.imported(total, count);
            }
          }
        };

    // register type adapters
    for (DataAdapter adapter : defaultAdapters) {
      binder.registerAdapter(adapter);
    }
    for (DataAdapter adapter : config.getAdapters()) {
      binder.registerAdapter(adapter);
    }
    for (DataAdapter adapter : input.getAdapters()) {
      binder.registerAdapter(adapter);
    }

    XStreamUtils.setupSecurity(stream);
    stream.setMode(XStream.NO_REFERENCES);
    stream.registerConverter(new ElementConverter(binder));

    final EntityManager em = JPA.em();
    final EntityTransaction txn = em.getTransaction();
    final boolean started = !txn.isActive();

    if (canClear) {
      em.setFlushMode(FlushModeType.COMMIT);
    }
    if (started) {
      txn.begin();
    }

    try {
      stream.fromXML(reader);
      binder.finish();
      if (txn.isActive() && started) {
        txn.commit();
        if (canClear) {
          em.clear();
        }
      }
    } catch (Exception e) {
      if (txn.isActive() && started) {
        txn.rollback();
      }
      throw new ImportException(e);
    }
  }
}
