/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.common;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** This class defines from static helper methods to deal with YAML. */
public class YamlUtils {

  public YamlUtils() {}

  public static Map<String, Object> loadYaml(File file) throws IOException {
    return loadYaml(file.toPath());
  }

  public static Map<String, Object> loadYaml(Path path) throws IOException {
    return loadYaml(path.toUri().toURL());
  }

  public static Map<String, Object> loadYaml(URL resource) throws IOException {
    if (resource == null) {
      return new HashMap<>();
    }

    try (InputStream stream = resource.openStream()) {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
      return yaml.load(stream);
    }
  }

  /**
   * Flatten {@link Map} into a flat {@link Map} with key names using property dot notation.
   *
   * @param source {@link Map} to flattern
   * @return result flattened {@link Map}
   */
  public static Map<String, String> getFlattenedMap(Map<String, Object> source) {
    Map<String, String> result = new LinkedHashMap<>();
    flattenMap(result, source.entrySet().iterator(), "");
    return result;
  }

  private static void flattenMap(
      Map<String, String> result, Iterator<Map.Entry<String, Object>> source, String prefix) {
    if (StringUtils.notBlank(prefix)) {
      prefix = prefix + ".";
    }

    while (source.hasNext()) {
      Map.Entry<String, Object> entry = source.next();
      flattenElement(result, entry.getValue(), prefix.concat(entry.getKey()));
    }
  }

  @SuppressWarnings("unchecked")
  private static void flattenElement(Map<String, String> result, Object source, String prefix) {
    if (source instanceof Iterable) {
      flattenCollection(result, (Iterable<Object>) source, prefix);
      return;
    }

    if (source instanceof Map) {
      flattenMap(result, ((Map<String, Object>) source).entrySet().iterator(), prefix);
      return;
    }

    result.put(prefix, source == null ? null : source.toString());
  }

  private static void flattenCollection(
      Map<String, String> result, Iterable<Object> iterable, String prefix) {
    int counter = 0;

    for (Object element : iterable) {
      flattenElement(result, element, prefix + "[" + counter + "]");
      counter++;
    }
  }
}
