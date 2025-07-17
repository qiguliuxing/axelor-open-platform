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
package com.axelor.test.db;

import com.axelor.db.Model;
import com.google.common.base.MoreObjects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.SequenceGenerator;
import javax.persistence.Table;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "TEST_CURRENCY")
public class Currency extends Model {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "TEST_CURRENCY_SEQ")
  @SequenceGenerator(
      name = "TEST_CURRENCY_SEQ",
      sequenceName = "TEST_CURRENCY_SEQ",
      allocationSize = 1)
  private Long id;

  @NotNull
  @Column(unique = true)
  private String code;

  @NotNull
  @Column(unique = true)
  private String name;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("id", getId())
        .add("code", getCode())
        .add("name", getName())
        .omitNullValues()
        .toString();
  }
}
