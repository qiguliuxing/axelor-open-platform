/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.meta.loader;

import com.axelor.common.StringUtils;
import com.axelor.common.csv.CSVFile;
import com.axelor.db.JPA;
import com.axelor.db.ParallelTransactionExecutor;
import com.axelor.meta.MetaScanner;
import com.axelor.meta.db.MetaTranslation;
import com.axelor.meta.db.repo.MetaTranslationRepository;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class I18nLoader extends AbstractParallelLoader {

  private static final Logger LOG = LoggerFactory.getLogger(I18nLoader.class);

  @Inject private MetaTranslationRepository translations;

  private static final String FILE_PATTERN = ".*(?:messages_|custom_)([a-zA-Z_-]+)\\.csv$";

  @Override
  protected void doLoad(URL file, Module module, boolean update) {
    LOG.debug("Load translation: {}", file.getFile());

    try (InputStream is = file.openStream()) {
      process(is, file.getFile());
    } catch (IOException e) {
      LOG.error("Unable to import file: {}", file.getFile());
    }
  }

  @Override
  protected void doLoad(Module module, boolean update) {
    splitFiles(findFiles(module)).forEach(files -> doLoad(files, module, update));
  }

  protected void doLoad(List<URL> files, Module module, boolean update) {
    for (URL file : files) {
      doLoad(file, module, update);
    }
  }

  @Override
  protected void feedTransactionExecutor(
      ParallelTransactionExecutor transactionExecutor,
      Module module,
      boolean update,
      Set<Path> paths) {

    for (List<URL> files : splitFiles(findFiles(module, paths))) {
      transactionExecutor.add(() -> doLoad(files, module, update));
    }
  }

  @Override
  protected List<URL> findFiles(Module module) {
    List<URL> files = MetaScanner.findAll(module.getName(), "i18n", FILE_PATTERN);
    // Make sure to put custom translation files at the end
    return files.stream()
        .sorted(
            Comparator.comparing(
                URL::toString,
                Comparator.comparing((String x) -> x.matches(".*(?:custom_)(\\w+)\\.csv$"))
                    .thenComparing(Comparator.naturalOrder())))
        .collect(Collectors.toList());
  }

  protected Collection<List<URL>> splitFiles(List<URL> urls) {
    Map<String, List<URL>> map = new HashMap<>();
    urls.forEach(
        o -> {
          Pattern pattern = Pattern.compile(FILE_PATTERN);
          Matcher matcher = pattern.matcher(o.getFile());

          if (!matcher.matches()) {
            return;
          }

          map.computeIfAbsent(
                  StringUtils.normalizeLanguageTag(matcher.group(1)), k -> new LinkedList<>())
              .add(o);
        });
    return map.values();
  }

  private void process(InputStream stream, String fileName) throws IOException {
    // Get language name from the file name
    Pattern pattern = Pattern.compile(FILE_PATTERN);
    Matcher matcher = pattern.matcher(fileName);

    if (!matcher.matches()) {
      return;
    }

    final String language = StringUtils.normalizeLanguageTag(matcher.group(1));
    final CSVFile csv = CSVFile.DEFAULT.withFirstRecordAsHeader();

    try (CSVParser csvParser = csv.parse(stream, StandardCharsets.UTF_8)) {

      int counter = 0;

      for (CSVRecord record : csvParser) {

        if (CSVFile.isEmpty(record)) {
          continue;
        }

        Map<String, String> map = record.toMap();

        String key = map.get("key");
        String message = map.get("message");

        if (StringUtils.isBlank(key) || StringUtils.isEmpty(message)) {
          continue;
        }

        MetaTranslation entity = translations.findByKey(key, language);
        if (entity == null) {
          entity = new MetaTranslation();
          entity.setKey(key);
          entity.setLanguage(language);
          entity.setMessage(message);
        } else if (!StringUtils.isBlank(message)) {
          entity.setMessage(message);
        }

        translations.save(entity);

        if (counter++ % 20 == 0) {
          JPA.em().flush();
          JPA.em().clear();
        }
      }
    }
  }
}
