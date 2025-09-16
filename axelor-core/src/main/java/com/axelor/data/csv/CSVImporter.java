/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.data.csv;

import com.axelor.common.StringUtils;
import com.axelor.common.csv.CSVFile;
import com.axelor.data.ImportException;
import com.axelor.data.ImportTask;
import com.axelor.data.Importer;
import com.axelor.data.Listener;
import com.axelor.data.adapter.DataAdapter;
import com.axelor.db.JPA;
import com.axelor.db.Model;
import com.axelor.db.internal.DBHelper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import jakarta.annotation.Nullable;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.io.input.BOMInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSVImporter implements Importer {

  private Logger LOG = LoggerFactory.getLogger(getClass());

  private File dataDir;

  private CSVConfig config;

  private List<Listener> listeners = new ArrayList<>();

  private List<String[]> valuesStack = new ArrayList<>();

  private Map<String, Object> context;

  private CSVLogger loggerManager;

  public void addListener(Listener listener) {
    this.listeners.add(listener);
  }

  public void clearListener() {
    this.listeners.clear();
  }

  public void setContext(Map<String, Object> context) {
    this.context = context;
  }

  public CSVImporter(String configFile) {
    this(configFile, null, null);
  }

  public CSVImporter(String config, String dataDir) {
    this(config, dataDir, null);
  }

  @Inject
  public CSVImporter(
      @Named("axelor.data.config") String config,
      @Named("axelor.data.dir") String dataDir,
      @Nullable @Named("axelor.error.dir") String errorDir) {

    File _file = new File(config);

    Objects.requireNonNull(_file);
    Preconditions.checkArgument(_file.isFile());

    if (dataDir != null) {
      File _data = new File(dataDir);
      Objects.requireNonNull(_data);
      Preconditions.checkArgument(_data.isDirectory());
      this.dataDir = _data;
    }

    this.config = CSVConfig.parse(_file);
    if (!Strings.isNullOrEmpty(errorDir)) {
      this.loggerManager = new CSVLogger(this.config, errorDir);
    }
  }

  public CSVImporter(CSVConfig config) {
    this(config, null);
  }

  public CSVImporter(CSVConfig config, String dataDir) {
    this(config, dataDir, null);
  }

  public CSVImporter(CSVConfig config, String dataDir, String errorDir) {

    if (dataDir != null) {
      File _data = new File(dataDir);
      Objects.requireNonNull(_data);
      Preconditions.checkArgument(_data.isDirectory());
      this.dataDir = _data;
    }

    this.config = config;
    if (!Strings.isNullOrEmpty(errorDir)) {
      this.loggerManager = new CSVLogger(this.config, errorDir);
    }
  }

  private List<File> getFiles(String... names) {
    List<File> all = new ArrayList<>();
    for (String name : names) all.add(new File(dataDir, name));
    return all;
  }

  public CSVLogger getLoggerManager() {
    return loggerManager;
  }

  /**
   * Run the task from the configured readers
   *
   * @param task the task to run
   */
  public void run(ImportTask task) {
    try {
      task.init();
      for (CSVInput input : config.getInputs()) {
        for (Reader reader : task.getReader(input.getFileName())) {
          try {
            this.process(input, reader);
          } catch (IOException e) {
            if (LOG.isErrorEnabled()) {
              LOG.error("I/O error while accessing {}.", input.getFileName());
            }
            if (!task.handle(e)) {
              break;
            }
          } catch (ClassNotFoundException e) {
            if (LOG.isErrorEnabled()) {
              LOG.error("Error while importing {}.", input.getFileName());
              LOG.error("No such class found {}.", input.getTypeName());
            }
            if (!task.handle(e)) {
              break;
            }
          } catch (Exception e) {
            if (!task.handle(new ImportException(e))) {
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

  @Override
  public void run() {

    for (CSVInput input : config.getInputs()) {

      String fileName = input.getFileName();
      List<File> files = this.getFiles(fileName);

      for (File file : files) {
        try {
          this.process(input, file);
        } catch (IOException e) {
          if (LOG.isErrorEnabled()) LOG.error("Error while accessing {}.", file);
        } catch (ClassNotFoundException e) {
          if (LOG.isErrorEnabled()) {
            LOG.error("Error while importing {}.", file);
            LOG.error("No such class found {}.", input.getTypeName());
          }
        } catch (Exception e) {
          if (LOG.isErrorEnabled()) {
            LOG.error("Error while importing {}.", file);
            LOG.error("Unable to import data.");
            LOG.error("With following exception:", e);
          }
        }
      }
    }
  }

  /**
   * Launch the import for the input and file.
   *
   * @param input
   * @param file
   * @throws IOException
   * @throws ClassNotFoundException
   */
  private void process(CSVInput input, File file) throws IOException, ClassNotFoundException {
    try (final Reader reader = newReader(new FileInputStream(file))) {
      this.process(input, reader);
    }
  }

  /** Creates a reader capable of handling BOMs. */
  private InputStreamReader newReader(final InputStream inputStream) throws IOException {
    return new InputStreamReader(
        BOMInputStream.builder().setInputStream(inputStream).get(), StandardCharsets.UTF_8);
  }

  /**
   * Launches the import for the input and reader.
   *
   * @param csvInput
   * @param reader
   * @throws IOException
   * @throws ClassNotFoundException
   */
  private void process(CSVInput csvInput, Reader reader)
      throws IOException, ClassNotFoundException {

    String beanName = csvInput.getTypeName();

    LOG.info("Importing {} from {}", beanName, csvInput.getFileName());

    int count = 0;
    int total = 0;
    int batchSize = DBHelper.getJdbcBatchSize();

    CSVFile csv = CSVFile.DEFAULT.withDelimiter(csvInput.getSeparator());
    if (StringUtils.isBlank(csvInput.getHeader())) {
      csv = csv.withFirstRecordAsHeader();
    } else {
      csv = csv.withHeader(csvInput.getHeader().trim().split("\\s*,\\s*"));
    }

    try (CSVParser csvParser = csv.parse(reader)) {

      String[] fields = CSVFile.header(csvParser);
      Class<?> beanClass = Class.forName(beanName);

      if (loggerManager != null) {
        loggerManager.prepareInput(csvInput, fields);
      }

      LOG.debug("Header {}", Arrays.asList(fields));

      CSVBinder binder = new CSVBinder(beanClass, fields, csvInput);

      JPA.em().getTransaction().begin();

      final Map<String, Object> context = new HashMap<>();

      // Put global context
      if (this.context != null) {
        context.putAll(this.context);
      }

      csvInput.callPrepareContext(context);

      // Put data path in context
      if (dataDir != null) {
        context.put("__path__", dataDir.toPath());
      }

      // register type adapters
      for (DataAdapter adapter : defaultAdapters) {
        binder.registerAdapter(adapter);
      }
      for (DataAdapter adapter : this.config.getAdapters()) {
        binder.registerAdapter(adapter);
      }
      for (DataAdapter adapter : csvInput.getAdapters()) {
        binder.registerAdapter(adapter);
      }

      // Process for each record
      for (CSVRecord record : csvParser) {

        if (CSVFile.isEmpty(record)) {
          continue;
        }

        String[] values = CSVFile.values(record);

        LOG.trace("Record {}", Arrays.asList(values));

        Object bean = null;
        try {
          bean = this.importRow(values, binder, csvInput, context, false);
          count++;
        } catch (Exception e) {
          int line = count + 1;
          LOG.error("Error while importing {}.", csvInput.getFileName());
          LOG.error(
              "Unable to import record #{}: {}",
              line,
              Arrays.asList(values),
              ImportException.from(e));

          // Recover the transaction
          if (JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().rollback();
          }

          if (!JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().begin();
          }

          for (Listener listener : listeners) {
            listener.handle((Model) bean, e);
          }

          // Re-parse previous records
          this.onRollback(values, binder, csvInput, context);
        }

        ++total;
        if (valuesStack.size() % batchSize == 0) {
          LOG.trace("Commit {} records", valuesStack.size());

          if (JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().commit();
            JPA.em().clear();
            valuesStack.clear();
          }
          if (!JPA.em().getTransaction().isActive()) {
            JPA.em().getTransaction().begin();
          }
        }
      }

      if (JPA.em().getTransaction().isActive()) {
        LOG.trace("Commit {} records", valuesStack.size());

        JPA.em().getTransaction().commit();
        JPA.em().clear();
      }
    } catch (Exception e) {
      if (JPA.em().getTransaction().isActive()) {
        JPA.em().getTransaction().rollback();
      }

      LOG.error("Error while importing {}.", csvInput.getFileName());
      LOG.error("Unable to import data.");
      LOG.error("With following exception:", e);
    } finally {
      for (Listener listener : listeners) {
        listener.imported(total, count);
      }

      valuesStack.clear();
    }
  }

  /**
   * Import the specific row.
   *
   * @param values
   * @param binder
   * @param csvInput
   * @param context
   * @param onRollback
   * @return the imported object
   * @throws Exception
   */
  private Object importRow(
      String[] values,
      CSVBinder binder,
      CSVInput csvInput,
      Map<String, Object> context,
      Boolean onRollback)
      throws Exception {
    Object bean = null;
    Map<String, Object> ctx = new HashMap<>(context);

    bean = binder.bind(values, ctx);

    bean = csvInput.call(bean, ctx);
    LOG.trace("bean created: {}", bean);

    if (bean != null) {
      JPA.manage((Model) bean);

      LOG.trace("bean saved: {}", bean);
    }

    if (!onRollback) {
      valuesStack.add(values);

      for (Listener listener : listeners) {
        listener.imported((Model) bean);
      }
    }

    return bean;
  }

  /**
   * Rollback previous rows stored in valuesStack.
   *
   * @param values
   * @param binder
   * @param csvInput
   * @param context
   */
  private void onRollback(
      String[] values, CSVBinder binder, CSVInput csvInput, Map<String, Object> context) {
    if (loggerManager != null) {
      loggerManager.log(values);
    }

    for (String[] row : valuesStack) {
      LOG.debug("Recover record {}", Arrays.asList(row));

      try {
        this.importRow(row, binder, csvInput, context, true);

        if (JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().commit();
        }
      } catch (Exception e) {
        if (JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().rollback();
        }
      } finally {
        if (!JPA.em().getTransaction().isActive()) {
          JPA.em().getTransaction().begin();
        }
      }
    }

    valuesStack.clear();
  }
}
