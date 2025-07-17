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

import com.axelor.db.JpaModel;
import com.google.common.base.MoreObjects;
import jakarta.persistence.Basic;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "CONTACT_ENUM_CHECK")
public class EnumCheck extends JpaModel {

  @Basic
  @Enumerated(EnumType.STRING)
  private EnumStatus status;

  @Basic
  @Type(type = "com.axelor.db.hibernate.type.ValueEnumType")
  private EnumStatusNumber statusNumber;

  public EnumStatus getStatus() {
    return status;
  }

  public void setStatus(EnumStatus status) {
    this.status = status;
  }

  public EnumStatusNumber getStatusNumber() {
    return statusNumber;
  }

  public void setStatusNumber(EnumStatusNumber statusNumber) {
    this.statusNumber = statusNumber;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(getClass())
        .add("status", status)
        .add("statusNumber", statusNumber)
        .toString();
  }
}
