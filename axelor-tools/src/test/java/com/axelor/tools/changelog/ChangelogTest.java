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
package com.axelor.tools.changelog;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axelor.common.ResourceUtils;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class ChangelogTest {

  @Test
  public void generateChangelogTest() throws URISyntaxException, IOException {
    String version = "1.0.0";
    String header = getHeader(version);

    ReleaseProcessor processor = new ReleaseProcessor();
    Release release =
        processor.process(getEntries(), version, header, ChangelogEntryConstants.TYPES, null);

    File outputFile =
        Path.of(ResourceUtils.getResource("changelogs/EXPECTED_CHANGELOG.md").toURI()).toFile();
    String output =
        com.google.common.io.Files.asCharSource(outputFile, StandardCharsets.UTF_8).read();

    assertEquals(output, new ReleaseGenerator().generate(release));
  }

  List<ChangelogEntry> getEntries() throws URISyntaxException, IOException {
    URL changelogsUrl = ResourceUtils.getResource("changelogs/entries/");
    ChangelogEntryParser parser = new ChangelogEntryParser();

    try (Stream<Path> stream = Files.list(Path.of(changelogsUrl.toURI())).sorted()) {
      return stream
          .map(Path::toFile)
          .map(
              it -> {
                try {
                  return parser.parse(it);
                } catch (IOException e) {
                  return null;
                }
              })
          .collect(Collectors.toList());
    }
  }

  String getHeader(String version) {
    return "%s (%s)".formatted(version, LocalDate.of(2020, 1, 10).format(DateTimeFormatter.ISO_LOCAL_DATE));
  }

  @Test
  public void generateEmptyReleaseTest() {
    String version = "1.0.0";
    String defaultContent = "No Changes";
    String header = getHeader(version);

    ReleaseProcessor processor = new ReleaseProcessor();
    Release release =
        processor.process(Collections.emptyList(), version, header, null, defaultContent);

    assertEquals(
        "## 1.0.0 (2020-01-10)\n\n" + defaultContent + "\n",
        new ReleaseGenerator().generate(release));
  }
}
