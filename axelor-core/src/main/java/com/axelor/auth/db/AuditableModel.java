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
package com.axelor.auth.db;

import com.axelor.db.Model;
import com.axelor.db.annotations.Widget;
import java.time.LocalDateTime;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;

/**
 * The base abstract class with update logging feature.
 *
 * <p>The model instance logs the creation date, last modified date, the authorized user who created
 * the record and the user who updated the record last time.
 */
@MappedSuperclass
public abstract class AuditableModel extends Model {

  @Widget(readonly = true, copyable = false, title = /*$$(*/ "Created on" /*)*/)
  private LocalDateTime createdOn;

  @Widget(readonly = true, copyable = false, title = /*$$(*/ "Updated on" /*)*/)
  private LocalDateTime updatedOn;

  @Widget(readonly = true, copyable = false, title = /*$$(*/ "Created by" /*)*/)
  @ManyToOne(
      fetch = FetchType.LAZY,
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  private User createdBy;

  @Widget(readonly = true, copyable = false, title = /*$$(*/ "Updated by" /*)*/)
  @ManyToOne(
      fetch = FetchType.LAZY,
      cascade = {CascadeType.PERSIST, CascadeType.MERGE})
  private User updatedBy;

  public LocalDateTime getCreatedOn() {
    return createdOn;
  }

  @Access(AccessType.FIELD)
  private void setCreatedOn(LocalDateTime createdOn) {
    this.createdOn = createdOn;
  }

  public LocalDateTime getUpdatedOn() {
    return updatedOn;
  }

  @Access(AccessType.FIELD)
  private void setUpdatedOn(LocalDateTime updatedOn) {
    this.updatedOn = updatedOn;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  @Access(AccessType.FIELD)
  private void setCreatedBy(User createdBy) {
    this.createdBy = createdBy;
  }

  public User getUpdatedBy() {
    return updatedBy;
  }

  @Access(AccessType.FIELD)
  private void setUpdatedBy(User updatedBy) {
    this.updatedBy = updatedBy;
  }
}
