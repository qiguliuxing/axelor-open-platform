/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db.hibernate.naming;

import com.axelor.db.internal.DBHelper;
import java.util.List;
import java.util.stream.Collectors;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.ImplicitConstraintNameSource;
import org.hibernate.boot.model.naming.ImplicitForeignKeyNameSource;
import org.hibernate.boot.model.naming.ImplicitIndexNameSource;
import org.hibernate.boot.model.naming.ImplicitJoinColumnNameSource;
import org.hibernate.boot.model.naming.ImplicitNamingStrategyLegacyHbmImpl;
import org.hibernate.boot.model.naming.ImplicitUniqueKeyNameSource;
import org.hibernate.boot.model.naming.NamingHelper;

public class ImplicitNamingStrategyImpl extends ImplicitNamingStrategyLegacyHbmImpl {

  private static final long serialVersionUID = -6890657585791247748L;

  public static final ImplicitNamingStrategyImpl INSTANCE = new ImplicitNamingStrategyImpl();

  @Override
  public Identifier determineForeignKeyName(ImplicitForeignKeyNameSource source) {
    return determineContraintName(source, "FK_");
  }

  @Override
  public Identifier determineUniqueKeyName(ImplicitUniqueKeyNameSource source) {
    return determineContraintName(source, "UK_");
  }

  @Override
  public Identifier determineIndexName(ImplicitIndexNameSource source) {
    // if oracle, generate short index names
    if (DBHelper.isOracle()) {
      return super.determineIndexName(source);
    }
    // in v4, we used _IDX suffix in index names
    return determineContraintName(source, "_IDX");
  }

  private Identifier determineContraintName(
      ImplicitConstraintNameSource source, String prefixOrSuffix) {
    final Identifier userProvidedIdentifier = source.getUserProvidedIdentifier();
    if (userProvidedIdentifier != null) {
      return userProvidedIdentifier;
    }
    final Identifier tableName = source.getTableName();
    final List<Identifier> columns =
        source.getColumnNames().stream()
            .map(PhysicalNamingStrategyImpl::addUnderscores)
            .collect(Collectors.toList());

    // if suffix
    if (prefixOrSuffix.startsWith("_")) {
      final String joined =
          columns.stream().map(Identifier::getText).collect(Collectors.joining("_"));
      return toIdentifier(tableName + "_" + joined + prefixOrSuffix, source.getBuildingContext());
    }

    return toIdentifier(
        NamingHelper.INSTANCE.generateHashedConstraintName(prefixOrSuffix, tableName, columns),
        source.getBuildingContext());
  }

  public Identifier determineJoinColumnName(ImplicitJoinColumnNameSource source) {
    if (source.getAttributePath() != null) {
      return super.determineJoinColumnName(source);
    }
    // ImprovedNamingStrategy in hibernate v4 used referenced table name
    final String name = source.getReferencedTableName().getText();
    return toIdentifier(name, source.getBuildingContext());
  }
}
