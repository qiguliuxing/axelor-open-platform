/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db;

import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import com.axelor.inject.Beans;
import jakarta.persistence.EntityManager;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * The JPA implementation of the {@link Repository}.
 *
 * @param <T> the type of bean class
 */
public class JpaRepository<T extends Model> implements Repository<T> {

  protected Class<T> modelClass;

  protected JpaRepository(Class<T> modelClass) {
    this.modelClass = modelClass;
  }

  @Override
  public List<Property> fields() {
    final Property[] fields = JPA.fields(modelClass);
    if (fields == null) {
      return null;
    }
    return Arrays.asList(fields);
  }

  @Override
  public Query<T> all() {
    return JPA.all(modelClass);
  }

  /**
   * Get the {@link Query} instance of the given type.
   *
   * @param <U> type of the model class
   * @param type the subtype of the managed model class.
   * @return instance of {@link Query}
   */
  public <U extends T> Query<U> all(Class<U> type) {
    return JPA.all(type);
  }

  @Override
  public T create(Map<String, Object> values) {
    return Mapper.toBean(modelClass, values);
  }

  @Override
  public T copy(T entity, boolean deep) {
    return JPA.copy(entity, deep);
  }

  @Override
  public T find(Long id) {
    return JPA.find(modelClass, id);
  }

  @Override
  public List<T> findByIds(List<Long> ids) {
    return JPA.findByIds(modelClass, ids);
  }

  @Override
  public T save(T entity) {
    return JPA.save(entity);
  }

  /**
   * Make an entity managed and persistent.
   *
   * @param entity the entity instance
   * @see EntityManager#persist(Object)
   */
  public void persist(T entity) {
    JPA.persist(entity);
  }

  /**
   * Merge the state of the given entity into the current persistence context.
   *
   * @param entity the entity instance
   * @return the managed instance
   * @see EntityManager#merge(Object)
   */
  public T merge(T entity) {
    return JPA.merge(entity);
  }

  @Override
  public void remove(T entity) {
    // detach orphan o2m records
    detachChildren(entity);
    JPA.remove(entity);
  }

  @SuppressWarnings("all")
  private void detachChildren(T entity) {
    if (!JPA.em().contains(entity)) {
      return;
    }
    final Mapper mapper = Mapper.of(EntityHelper.getEntityClass(entity));
    for (Property property : mapper.getProperties()) {
      if ((property.getType() != PropertyType.ONE_TO_MANY
              && property.getType() != PropertyType.ONE_TO_ONE)
          || !property.isOrphan()) {
        continue;
      }

      final Property mappedBy = Mapper.of(property.getTarget()).getProperty(property.getMappedBy());
      if (mappedBy != null && mappedBy.isRequired()) {
        continue;
      }

      final Object value = property.get(entity);
      final Collection<? extends Model> items;

      if (value instanceof Collection) {
        items = (Collection<? extends Model>) value;
      } else if (value instanceof Model model) {
        items = Collections.singletonList(model);
      } else {
        continue;
      }

      for (Model item : items) {
        property.setAssociation(item, null);
      }
    }
  }

  /**
   * Refresh the state of the instance from the database, overwriting changes made to the entity, if
   * any.
   *
   * @param entity the entity instance
   * @see EntityManager#refresh(Object)
   */
  @Override
  public void refresh(T entity) {
    JPA.refresh(entity);
  }

  /**
   * Synchronize the persistence context to the underlying database.
   *
   * @see EntityManager#flush()
   */
  @Override
  public void flush() {
    JPA.flush();
  }

  @Override
  public Map<String, Object> validate(Map<String, Object> json, Map<String, Object> context) {
    return json;
  }

  @Override
  public Map<String, Object> populate(Map<String, Object> json, Map<String, Object> context) {
    return json;
  }

  @SuppressWarnings("unchecked")
  public static <U extends Model> JpaRepository<U> of(Class<U> type) {
    final Class<?> klass = JpaScanner.findRepository(type.getSimpleName() + "Repository");
    if (klass != null) {
      final JpaRepository<U> repo = (JpaRepository<U>) Beans.get(klass);
      if (repo.modelClass.isAssignableFrom(type)) {
        return repo;
      }
    }
    return Beans.inject(new JpaRepository<>(type));
  }
}
