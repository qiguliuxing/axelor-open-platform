/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.tools.changelog;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class ReleaseProcessor {

  public Release process(
      Collection<ChangelogEntry> changelogEntries,
      String version,
      String header,
      List<String> types,
      String defaultContent) {

    Objects.requireNonNull(version);
    Objects.requireNonNull(header);
    Objects.requireNonNull(changelogEntries);

    validate(changelogEntries);
    adjustEntriesTypes(changelogEntries, types);

    Release release = new Release();
    release.setVersion(version);
    release.setHeader(header);
    release.setDefaultContent(defaultContent);

    LinkedHashMap<String, List<ChangelogEntry>> entriesGroupedByType =
        changelogEntries.stream()
            .sorted(Comparator.comparingInt(e -> types.indexOf(e.getType())))
            .collect(
                Collectors.groupingBy(
                    ChangelogEntry::getType, LinkedHashMap::new, Collectors.toList()));
    release.setEntries(entriesGroupedByType);

    return release;
  }

  private void adjustEntriesTypes(Collection<ChangelogEntry> changelogEntries, List<String> types) {
    for (ChangelogEntry changelogEntry : changelogEntries) {
      changelogEntry.setType(getTargetType(changelogEntry.getType(), types));
    }
  }

  private String getTargetType(String type, List<String> types) {
    for (String targetType : types) {
      if (type.equalsIgnoreCase(targetType)) {
        return targetType;
      }
    }
    throw new IllegalArgumentException(
        "Type %s cannot be found in %s".formatted(type, String.join(",", types)));
  }

  private void validate(Collection<ChangelogEntry> changelogEntries) {
    Objects.requireNonNull(changelogEntries);

    Optional<ChangelogEntry> entryWithNullType =
        changelogEntries.stream().filter(entry -> entry.getType() == null).findFirst();
    if (entryWithNullType.isPresent()) {
      throw new IllegalArgumentException(
          "Type cannot be null in changelog entry: " + entryWithNullType.get());
    }

    Optional<ChangelogEntry> entryWithNullTitle =
        changelogEntries.stream().filter(entry -> entry.getTitle() == null).findFirst();
    if (entryWithNullTitle.isPresent()) {
      throw new IllegalArgumentException(
          "Title cannot be null in changelog entry: " + entryWithNullTitle.get());
    }
  }
}
