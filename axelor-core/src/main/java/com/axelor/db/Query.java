/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.db;

import com.axelor.auth.db.AuditableModel;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.hibernate.type.JsonFunction;
import com.axelor.db.internal.DBHelper;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.db.mapper.PropertyType;
import com.axelor.i18n.I18n;
import com.axelor.rpc.Resource;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.TypedQuery;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code Query} class allows filtering and fetching records quickly.
 *
 * <p>It also provides {@link #update(Map)} and {@link #delete()} method to perform mass update and
 * delete operation on matched records.
 */
public class Query<T extends Model> {

  private Class<T> beanClass;

  private String filter;

  private Object[] params;

  private Map<String, Object> namedParams;

  private String orderBy;

  private List<String> orderNames;

  private JoinHelper joinHelper;

  private boolean cacheable;

  private boolean readOnly;

  private boolean translate;

  private FlushModeType flushMode = FlushModeType.AUTO;

  private static final String NAME_PATTERN = "((?:[a-zA-Z_]\\w+)(?:(?:\\[\\])?\\.\\w+)*)";

  private static final Pattern PLACEHOLDER_PLAIN = Pattern.compile("(?<!\\?)\\?(?!(\\d+|\\?))");
  private static final Pattern PLACEHOLDER_INDEXED = Pattern.compile("\\?\\d+");

  /**
   * Create a new instance of {@code Query} with given bean class.
   *
   * @param beanClass model bean class
   */
  public Query(Class<T> beanClass) {
    this.beanClass = beanClass;
    this.orderBy = "";
    this.orderNames = new ArrayList<>();
    this.joinHelper = new JoinHelper(beanClass);
  }

  public static <T extends Model> Query<T> of(Class<T> klass) {
    return new Query<>(klass);
  }

  protected EntityManager em() {
    return JPA.em();
  }

  protected JoinHelper getJoinHelper() {
    return joinHelper;
  }

  protected String getFilter() {
    return filter;
  }

  protected void setFilter(String filter) {
    this.filter = filter;
  }

  protected Object[] getParams() {
    return params;
  }

  protected void setParams(Object[] params) {
    this.params = params;
  }

  protected Map<String, Object> getNamedParams() {
    return namedParams;
  }

  protected void setNamedParams(Map<String, Object> namedParams) {
    this.namedParams = namedParams;
  }

  /**
   * A convenient method to filter the query using JPQL's <i>where</i> clause.
   *
   * <p>The filter string should refer the field names with {@code self.} prefix and values should
   * not be embedded into the filter string but should be passed by parameters and {@code ?}
   * placeholder should be used to mark parameter substitutions.
   *
   * <p>Here is an example:
   *
   * <pre>
   * Query&lt;Person&gt; query = Query.of(Person);
   * query = query.filter(&quot;self.name = ? AND self.age &gt;= ?&quot;, &quot;some&quot;, 20);
   *
   * List&lt;Person&gt; matched = query.fetch();
   * </pre>
   *
   * <p>This is equivalent to:
   *
   * <pre>
   * SELECT self from Person self WHERE (self.name = ?1) AND (self.age &gt;= ?2)
   * </pre>
   *
   * <p>The params passed will be added as positional parameters to the JPA query object before
   * performing {@link #fetch()}.
   *
   * @param filter the filter string
   * @param params the parameters
   * @return the same instance
   */
  public Query<T> filter(String filter, Object... params) {
    if (this.filter != null) {
      throw new IllegalStateException("Query is already filtered.");
    }
    if (StringUtils.isBlank(filter)) {
      throw new IllegalArgumentException("filter string is required.");
    }

    // check for mixed style positional parameters
    if (PLACEHOLDER_PLAIN.matcher(filter).find() && PLACEHOLDER_INDEXED.matcher(filter).find()) {
      throw new IllegalArgumentException(
          "JDBC and JPA-style positional parameters can't be mixed: " + filter);
    }

    this.filter = joinHelper.parse(fixPlaceholders(filter), translate);
    this.params = params;
    return this;
  }

  public Query<T> filter(String filter) {
    final Object[] params = {};
    return filter(filter, params);
  }

  protected String fixPlaceholders(String filter) {
    // fix JDBC style parameters
    int i = 1;
    final Matcher matcher = PLACEHOLDER_PLAIN.matcher(filter);
    final StringBuffer sb = new StringBuffer();
    while (matcher.find()) {
      matcher.appendReplacement(sb, "?" + (i++));
    }
    matcher.appendTail(sb);
    return sb.toString();
  }

  /**
   * Set order by clause for the query. This method can be chained to provide multiple fields.
   *
   * <p>The {@code spec} is just a field name for {@code ASC} or should be prefixed with {@code -}
   * for {@code DESC} clause.
   *
   * <p>For example:
   *
   * <pre>
   * Query&lt;Person&gt; query = Query.of(Person);
   * query = query.filter(&quot;name =&quot;, &quot;some&quot;).filter(&quot;age &gt;=&quot;, 20)
   *        .filter(&quot;lang in&quot;, &quot;en&quot;, &quot;hi&quot;);
   *
   * query = query.order(&quot;name&quot;).order(&quot;-age&quot;);
   * </pre>
   *
   * <p>This is equivalent to:
   *
   * <pre>
   * SELECT p from Person p WHERE (p.name = ?1) AND (p.age &gt;= ?2) AND (lang IN (?3, ?4)) ORDER BY p.name, p.age DESC
   * </pre>
   *
   * @param spec order spec
   * @return the same query instance
   */
  public Query<T> order(String spec) {
    if (!orderBy.isEmpty()) {
      orderBy += ", ";
    } else {
      orderBy = " ORDER BY ";
    }

    String name = spec.trim();

    if (name.matches("(-)?\\s*(self\\.).*")) {
      throw new IllegalArgumentException(
          "Query#order(String) called with 'self' prefixed argument: " + spec);
    }

    if (name.charAt(0) == '-') {
      name = this.joinHelper.joinName(name.substring(1), true, translate);
      orderBy += name + " DESC";
    } else {
      name = this.joinHelper.joinName(name, true, translate);
      orderBy += name;
    }

    orderNames.add(name);

    return this;
  }

  /**
   * Set the query result cacheable.
   *
   * @return the same query instance
   */
  public Query<T> cacheable() {
    this.cacheable = true;
    return this;
  }

  /**
   * Set the query readonly.
   *
   * @return the same query instance.
   */
  public Query<T> readOnly() {
    this.readOnly = true;
    return this;
  }

  /**
   * Set whether to use translation join.
   *
   * @param translate
   * @return
   */
  public Query<T> translate(boolean translate) {
    this.translate = translate;
    return this;
  }

  /**
   * Use translation join.
   *
   * @return the same query instance.
   */
  public Query<T> translate() {
    return translate(true);
  }

  public Query<T> autoFlush(boolean auto) {
    this.flushMode = auto ? FlushModeType.AUTO : FlushModeType.COMMIT;
    return this;
  }

  /**
   * Fetch all the matched records as {@link Stream}.
   *
   * <p>Recommended only when dealing with large data, for example, batch processing. For normal use
   * cases, the {@link #fetch()} is more appropriate.
   *
   * <p>Also configure <code>hibernate.jdbc.fetch_size</code> (default is 20) to fine tune the fetch
   * size.
   *
   * @return stream of matched records.
   * @see #fetchStream(int)
   * @see #fetchStream(int, int)
   */
  public Stream<T> fetchStream() {
    return fetchStream(0, 0);
  }

  /**
   * Fetch the matched records as {@link Stream} with the given limit.
   *
   * <p>Recommended only when dealing with large data, for example, batch processing. For normal use
   * cases, the {@link #fetch()} is more appropriate.
   *
   * <p>Also configure <code>hibernate.jdbc.fetch_size</code> (default is 20) to fine tune the fetch
   * size.
   *
   * @param limit the limit
   * @return stream of matched records within the limit
   * @see #fetchStream(int, int)
   */
  public Stream<T> fetchStream(int limit) {
    return fetchStream(limit, 0);
  }

  /**
   * Fetch the matched records as {@link Stream} within the given range.
   *
   * <p>Recommended only when dealing with large data, for example, batch processing. For normal use
   * cases, the {@link #fetch()} is more appropriate.
   *
   * <p>Also configure <code>hibernate.jdbc.fetch_size</code> (default is 20) to fine tune the fetch
   * size.
   *
   * @param limit the limit
   * @param offset the offset
   * @return stream of matched records within the range
   */
  public Stream<T> fetchStream(int limit, int offset) {
    final org.hibernate.query.Query<T> query =
        (org.hibernate.query.Query<T>) fetchQuery(limit, offset);
    if (limit <= 0) {
      query.setFetchSize(DBHelper.getJdbcFetchSize());
    }
    return query.stream();
  }

  /**
   * Fetch all the matched records.
   *
   * @return list of all the matched records.
   */
  public List<T> fetch() {
    return fetch(0, 0);
  }

  /**
   * Fetch the matched records with the given limit.
   *
   * @param limit the limit
   * @return matched records within the limit
   */
  public List<T> fetch(int limit) {
    return fetch(limit, 0);
  }

  /**
   * Fetch the matched records within the given range.
   *
   * @param limit the limit
   * @param offset the offset
   * @return list of matched records within the range
   */
  public List<T> fetch(int limit, int offset) {
    return fetchQuery(limit, offset).getResultList();
  }

  private TypedQuery<T> fetchQuery(int limit, int offset) {
    final TypedQuery<T> query = em().createQuery(selectQuery(), beanClass);
    if (limit > 0) {
      query.setMaxResults(limit);
    }
    if (offset > 0) {
      query.setFirstResult(offset);
    }

    final QueryBinder binder = this.bind(query).opts(cacheable, flushMode);
    if (readOnly) {
      binder.setReadOnly();
    }
    return query;
  }

  /**
   * Fetch the first matched record.
   *
   * @return the first matched record, or null if there are no results.
   */
  public T fetchOne() {
    return fetchOne(0);
  }

  /**
   * Fetch a matched record at the given offset.
   *
   * @param offset the offset
   * @return the matched record at given offset, or null if there are no results.
   */
  public T fetchOne(int offset) {
    List<T> resultList = fetch(1, offset);
    return resultList == null || resultList.isEmpty() ? null : resultList.getFirst();
  }

  /**
   * Returns the number of total records matched.
   *
   * @return total number
   */
  public long count() {
    final TypedQuery<Long> query = em().createQuery(countQuery(), Long.class);
    this.bind(query).setCacheable(cacheable).setFlushMode(flushMode).setReadOnly();
    return query.getSingleResult();
  }

  /**
   * Return a selector to select records with specific fields only.
   *
   * @param names field names to select
   * @return a new instance of {@link Selector}
   */
  public Selector select(String... names) {
    return new Selector(names);
  }

  /**
   * Perform mass update on the matched records with the given values.
   *
   * @param values the key value map
   * @return total number of records updated
   */
  public int update(Map<String, Object> values) {
    return update(values, null);
  }

  /**
   * This is similar to {@link #update(Map)} but updates only single field.
   *
   * @param name the field name whose value needs to be changed
   * @param value the new value
   * @return total number of records updated
   */
  public int update(String name, Object value) {
    return update(Collections.singletonMap(name, value));
  }

  /**
   * Perform mass update on matched records with the given values.
   *
   * <p>If <code>updatedBy</code> user is null, perform non-versioned update otherwise performed
   * versioned update.
   *
   * @param values the key value map
   * @param updatedBy the user to set "updatedBy" field
   * @return total number of records updated
   */
  public int update(Map<String, Object> values, User updatedBy) {
    if (ObjectUtils.isEmpty(values)) {
      return 0;
    }

    final Map<String, Object> params = new HashMap<>();
    final Map<String, Object> namedParams = new HashMap<>();
    final List<String> where = new ArrayList<>();

    if (this.namedParams != null) {
      namedParams.putAll(this.namedParams);
    }

    for (final Entry<String, Object> entry : values.entrySet()) {
      String name = entry.getKey().replaceFirst("^self\\.", "");
      Object value = entry.getValue();
      params.put(name, value);
      if (value == null) {
        where.add("self." + name + " IS NOT NULL");
      } else {
        where.add("(self." + name + " IS NULL OR " + "self." + name + " != :" + name + ")");
      }
    }

    if (updatedBy != null && AuditableModel.class.isAssignableFrom(beanClass)) {
      params.put("updatedBy", updatedBy);
      params.put("updatedOn", LocalDateTime.now());
    }

    namedParams.putAll(params);

    boolean versioned = updatedBy != null;
    boolean notMySQL = !DBHelper.isMySQL();

    String whereClause = String.join(" OR ", where);
    String selectQuery = updateQuery().replaceFirst("SELECT self", "SELECT self.id");

    if (selectQuery.contains(" WHERE ")) {
      selectQuery = selectQuery.replaceFirst(" WHERE ", " WHERE (" + whereClause + ") AND (") + ")";
    } else {
      selectQuery = selectQuery + " WHERE " + whereClause;
    }

    selectQuery = selectQuery.replaceAll("\\bself", "that");

    if (notMySQL) {
      return QueryBinder.of(
              em().createQuery(updateQuery(params, versioned, "self.id IN (" + selectQuery + ")")))
          .bind(namedParams, this.params)
          .getQuery()
          .executeUpdate();
    }

    // MySQL doesn't allow sub select on same table with UPDATE also, JPQL doesn't
    // support JOIN with UPDATE query so we have to update in batch.

    String updateQuery = updateQuery(params, versioned, "self.id IN (:ids)");

    int count = 0;
    int limit = 1000;

    TypedQuery<Long> sq = em().createQuery(selectQuery, Long.class);
    jakarta.persistence.Query uq = em().createQuery(updateQuery);

    QueryBinder.of(sq).bind(namedParams, this.params);
    QueryBinder.of(uq).bind(namedParams, this.params);

    sq.setFirstResult(0);
    sq.setMaxResults(limit);

    List<Long> ids = sq.getResultList();
    while (!ids.isEmpty()) {
      uq.setParameter("ids", ids);
      count += uq.executeUpdate();
      ids = sq.getResultList();
    }

    return count;
  }

  /**
   * This is similar to {@link #update(Map, User)} but updates only single field.
   *
   * @param name the field name whose value needs to be changed
   * @param value the new value
   * @param updatedBy the user to set "updatedBy" field
   * @return total number of records updated
   */
  public int update(String name, Object value, User updatedBy) {
    return update(Collections.singletonMap(name, value), updatedBy);
  }

  /**
   * Bulk delete all the matched records. <br>
   * <br>
   * This method uses <code>DELETE</code> query and performs {@link
   * jakarta.persistence.Query#executeUpdate()}.
   *
   * @see #remove()
   * @return total number of records affected.
   */
  public int delete() {
    boolean notMySQL = !DBHelper.isMySQL();
    String selectQuery =
        updateQuery().replaceFirst("SELECT self", "SELECT self.id").replaceAll("\\bself", "that");

    if (notMySQL) {
      jakarta.persistence.Query q =
          em().createQuery(deleteQuery("self.id IN (" + selectQuery + ")"));
      this.bind(q);
      return q.executeUpdate();
    }

    // MySQL doesn't allow sub select on same table with DELETE also, JPQL doesn't
    // support JOIN with DELETE query so we have to update in batch.

    TypedQuery<Long> sq = em().createQuery(selectQuery, Long.class);
    jakarta.persistence.Query dq = em().createQuery(deleteQuery("self.id IN (:ids)"));

    this.bind(sq);
    this.bind(dq);

    int count = 0;
    int limit = 1000;

    sq.setFirstResult(0);
    sq.setMaxResults(limit);

    List<Long> ids = sq.getResultList();
    while (!ids.isEmpty()) {
      dq.setParameter("ids", ids);
      count += dq.executeUpdate();
      ids = sq.getResultList();
    }

    return count;
  }

  /**
   * Remove all the matched records. <br>
   * <br>
   * In contrast to the {@link #delete()} method, it performs {@link EntityManager#remove(Object)}
   * operation by fetching objects in pages (100 at a time).
   *
   * @see #delete()
   * @return total number of records removed.
   */
  public long remove() {
    try (final Stream<T> stream = fetchStream()) {
      return stream
          .map(
              item -> {
                JPA.remove(item);
                return item;
              })
          .count();
    }
  }

  protected String selectQuery(boolean update) {
    StringBuilder sb =
        new StringBuilder("SELECT self FROM ")
            .append(beanClass.getSimpleName())
            .append(" self")
            .append(joinHelper.toString(!update));
    if (filter != null && !filter.trim().isEmpty()) sb.append(" WHERE ").append(filter);
    if (update) {
      return sb.toString();
    }
    sb.append(orderBy);
    return joinHelper.fixSelect(sb.toString());
  }

  protected String selectQuery() {
    return selectQuery(false);
  }

  protected String updateQuery() {
    return selectQuery(true);
  }

  protected String countQuery() {
    StringBuilder sb =
        new StringBuilder("SELECT COUNT(self.id) FROM ")
            .append(beanClass.getSimpleName())
            .append(" self")
            .append(joinHelper.toString(false));
    if (filter != null && !filter.trim().isEmpty()) sb.append(" WHERE ").append(filter);
    return joinHelper.fixSelect(sb.toString());
  }

  protected String updateQuery(Map<String, Object> values, boolean versioned, String filter) {
    final String items =
        values.keySet().stream()
            .map(key -> "self.%s = :%s".formatted(key, key))
            .collect(Collectors.joining(", "));

    final StringBuilder sb =
        new StringBuilder("UPDATE ")
            .append(versioned ? "VERSIONED " : "")
            .append(beanClass.getSimpleName())
            .append(" self")
            .append(" SET ")
            .append(items);

    if (StringUtils.notBlank(filter)) {
      sb.append(" WHERE ").append(filter);
    }

    return sb.toString();
  }

  protected String deleteQuery(String filter) {
    final StringBuilder sb =
        new StringBuilder("DELETE FROM ").append(beanClass.getSimpleName()).append(" self");
    if (StringUtils.notBlank(filter)) {
      sb.append(" WHERE ").append(filter);
    }
    return sb.toString();
  }

  protected QueryBinder bind(jakarta.persistence.Query query) {
    return QueryBinder.of(query).bind(namedParams, params);
  }

  /**
   * Bind the named parameters of the query with the given values. Named parameter must me set after
   * query is filtered.
   *
   * @param params mapping for named params.
   * @return the same instance
   */
  public Query<T> bind(Map<String, Object> params) {
    if (this.namedParams == null) {
      this.namedParams = new HashMap<>();
    }
    if (params != null) {
      this.namedParams.putAll(params);
    }
    return this;
  }

  /**
   * Bind the given named parameter of the query with the given value.
   *
   * @param name the named parameter to bind
   * @param value the parameter value
   * @return the same instance
   */
  public Query<T> bind(String name, Object value) {
    Map<String, Object> params = new HashMap<>();
    params.put(name, value);
    return this.bind(params);
  }

  @Override
  public String toString() {
    return selectQuery();
  }

  /**
   * A helper class to select specific field values. The record is returned as a Map object with the
   * given names as keys.
   *
   * <pre>
   * List&lt;Map&gt; data = Contact.all().filter(&quot;self.age &gt; ?&quot;, 20)
   *        .select(&quot;title.name&quot;, &quot;fullName&quot;, &quot;age&quot;).fetch(80, 0);
   * </pre>
   *
   * This results in following query:
   *
   * <pre>
   * SELECT _title.name, self.fullName JOIN LEFT self.title AS _title WHERE self.age &gt; ? LIMIT 80
   * </pre>
   *
   * The {@link Selector#fetch(int, int)} method returns a List of Map instead of the model object.
   */
  public class Selector {

    private List<String> names = Lists.newArrayList("id", "version");
    private List<String> collections = new ArrayList<>();
    private String query;
    private Mapper mapper = Mapper.of(beanClass);

    private Selector(String... names) {
      List<String> selects = new ArrayList<>();
      selects.add("self.id");
      selects.add("self.version");
      for (String name : names) {
        Property property = getProperty(name);
        if (property != null
            && property.getType() != PropertyType.BINARY
            && !property.isTransient()
            && !hasTransientParent(name)) {
          String alias = joinHelper.joinName(name);
          if (alias != null) {
            selects.add(alias);
            this.names.add(name);
          } else {
            collections.add(name);
          }
          // select id,version,name field for m2o
          if (property.isReference() && property.getTargetName() != null) {
            this.names.add(name + ".id");
            this.names.add(name + ".version");
            this.names.add(name + "." + property.getTargetName());
            selects.add(joinHelper.joinName(name + ".id"));
            selects.add(joinHelper.joinName(name + ".version"));
            selects.add(joinHelper.joinName(name + "." + property.getTargetName()));
          }
        } else if (name.indexOf('.') > -1) {
          final JsonFunction func = JsonFunction.fromPath(name);
          final Property json = mapper.getProperty(func.getField());
          if (json != null && json.isJson()) {
            this.names.add(func.getField() + "." + func.getAttribute());
            selects.add(func.toString());
          }
        }
      }

      if (joinHelper.hasCollection) {
        orderNames.stream().filter(n -> !selects.contains(n)).forEach(selects::add);
      }

      StringBuilder sb =
          new StringBuilder("SELECT")
              .append(" new List(" + Joiner.on(", ").join(selects) + ")")
              .append(" FROM ")
              .append(beanClass.getSimpleName())
              .append(" self")
              .append(joinHelper.toString(false));
      if (filter != null && !filter.trim().isEmpty()) sb.append(" WHERE ").append(filter);
      sb.append(orderBy);
      query = joinHelper.fixSelect(sb.toString());
    }

    private boolean hasTransientParent(String fieldName) {
      final List<String> fieldNameParts = Splitter.on('.').splitToList(fieldName);

      for (int i = 1; i < fieldNameParts.size(); ++i) {
        final String name = Joiner.on('.').join(fieldNameParts.subList(0, i));
        final Property property = getProperty(name);
        if (property != null && property.isTransient()) {
          return true;
        }
      }

      return false;
    }

    private Property getProperty(String field) {
      if (field == null || "".equals(field.trim())) return null;
      Mapper mapper = this.mapper;
      Property property = null;
      Iterator<String> names = Splitter.on(".").split(field).iterator();
      while (names.hasNext()) {
        property = mapper.getProperty(names.next());
        if (property == null) return null;
        if (names.hasNext()) {
          if (property.getTarget() == null) return null;
          mapper = Mapper.of(property.getTarget());
        }
      }
      return property;
    }

    @SuppressWarnings("all")
    public List<List> values(int limit, int offset) {
      jakarta.persistence.Query q = em().createQuery(query);
      if (limit > 0) {
        q.setMaxResults(limit);
      }
      if (offset > 0) {
        q.setFirstResult(offset);
      }

      final QueryBinder binder = bind(q).opts(cacheable, flushMode);
      if (readOnly) {
        binder.setReadOnly();
      }

      return q.getResultList();
    }

    @SuppressWarnings("all")
    public List<Map> fetch(int limit, int offset) {

      List<List> data = values(limit, offset);
      List<Map> result = new ArrayList<>();

      for (List items : data) {
        Map<String, Object> map = new HashMap<>();
        for (int i = 0; i < names.size(); i++) {
          Object value = items.get(i);
          String name = names.get(i);
          Property property = getProperty(name);
          // in case of m2o, get the id,version,name tuple
          if (property != null && property.isReference() && property.getTargetName() != null) {
            value = getReferenceValue(items, i);
            i += 3;
          } else if (value instanceof Model) {
            value = Resource.toMapCompact(value);
          }
          map.put(name, value);
        }
        if (collections.size() > 0) {
          map.putAll(this.fetchCollections(items.getFirst()));
        }
        result.add(map);
      }

      return result;
    }

    private Object getReferenceValue(List<?> items, int at) {
      if (items.get(at) == null && items.get(at + 1) == null) {
        return null;
      }
      Map<String, Object> value = new HashMap<>();
      String name = names.get(at);
      String nameField = names.get(at + 3).replace(name + ".", "");

      value.put("id", items.get(at + 1));
      value.put("$version", items.get(at + 2));
      value.put(nameField, items.get(at + 3));

      return value;
    }

    @SuppressWarnings("all")
    private Map<String, List> fetchCollections(Object id) {
      Map<String, List> result = new HashMap<>();
      Object self = JPA.em().find(beanClass, id);
      for (String name : collections) {
        Collection<Model> items = (Collection<Model>) mapper.get(self, name);
        if (items != null) {
          List<Object> all = new ArrayList<>();
          for (Model obj : items) {
            all.add(Resource.toMapCompact(obj));
          }
          result.put(name, all);
        }
      }
      return result;
    }

    @Override
    public String toString() {
      return query;
    }
  }

  /**
   * JoinHelper class is used to auto generate <code>LEFT JOIN</code> for association expressions.
   *
   * <p>For example:
   *
   * <pre>
   *    Query&lt;Contact&gt; q = Contact.all().filter("self.title.code = ?1 OR self.age &gt; ?2", "mr", 20);
   * </pre>
   *
   * Results in:
   *
   * <pre>
   * SELECT self FROM Contact self LEFT JOIN self.title _title WHERE _title.code = ?1 OR self.age &gt; ?2
   * </pre>
   *
   * So that all the records are matched even if <code>title</code> field is null.
   */
  protected static class JoinHelper {

    private Class<?> beanClass;

    private Map<String, String> joins = new LinkedHashMap<>();

    private Set<String> translationJoins = new HashSet<>();

    private Set<String> fetches = new HashSet<>();

    private boolean hasCollection;

    private static final Pattern selectPattern =
        Pattern.compile("^SELECT\\s+(COUNT\\s*\\()?", Pattern.CASE_INSENSITIVE);

    private static final Pattern pathPattern = Pattern.compile("self\\." + NAME_PATTERN);

    public JoinHelper(Class<?> beanClass) {
      this.beanClass = beanClass;
    }

    /**
     * Parse the given filter string and return transformed filter expression.
     *
     * <p>Automatically calculate <code>LEFT JOIN</code> for association path expressions and the
     * path expressions are replaced with the join variables.
     *
     * @param filter the filter expression
     * @param translate whether to generate translation join
     * @return the transformed filter expression
     */
    public String parse(String filter, boolean translate) {

      String result = "";
      Matcher matcher = pathPattern.matcher(filter);

      int last = 0;
      while (matcher.find()) {
        MatchResult matchResult = matcher.toMatchResult();
        String alias = joinName(matchResult.group(1), false, translate);
        if (alias == null) {
          alias = "self." + matchResult.group(1);
        }
        result += filter.substring(last, matchResult.start()) + alias;
        last = matchResult.end();
      }
      if (last < filter.length()) result += filter.substring(last);

      return result;
    }

    /**
     * Automatically generate <code>LEFT JOIN</code> for the given name (association path
     * expression) and return the join variable.
     *
     * @param name the path expression or field name
     * @param fetch whether to generate fetch join
     * @param translate whether to generate translation join
     * @return join variable if join is created else returns name
     */
    private String joinName(String name, boolean fetch, boolean translate) {
      Mapper mapper = Mapper.of(beanClass);
      String[] path = name.split("\\.");
      String prefix = null;
      String variable = name;

      if (path.length > 1) {
        variable = path[path.length - 1];
        String joinOn = null;
        Mapper currentMapper = mapper;
        for (int i = 0; i < path.length - 1; i++) {
          String item = path[i].replace("[]", "");
          Property property = currentMapper.getProperty(item);
          if (property == null) {
            throw new org.hibernate.QueryException(
                "could not resolve property: "
                    + item
                    + " of: "
                    + currentMapper.getBeanClass().getName(),
                (String) null);
          }

          if (property.isJson()) {
            String jsonPath =
                i == 0
                    ? name
                    : String.join(
                        ".",
                        item,
                        Arrays.stream(path).skip(i + 1).collect(Collectors.joining(".")));
            return JsonFunction.fromPath(i == 0 ? "self" : prefix, jsonPath).toString();
          }

          if (prefix == null) {
            joinOn = "self." + item;
            prefix = "_" + item;

            // Use at least one join fetch on collection,
            // so that we still get unique results when not passing distinct to SQL
            if (fetches.isEmpty() && property.isCollection()) {
              fetches.add(joinOn);
            }
          } else {
            joinOn = prefix + "." + item;
            prefix = prefix + "_" + item;
          }
          if (!joins.containsKey(joinOn)) {
            joins.put(joinOn, prefix);
          }
          if (fetch) {
            fetches.add(joinOn);
          }

          if (property.getTarget() != null) {
            currentMapper = Mapper.of(property.getTarget());
            if (property.isCollection()) {
              this.hasCollection = true;
            }
          }

          if (i == path.length - 2) {
            property = currentMapper.getProperty(variable);
            if (property == null) {
              throw new IllegalArgumentException(
                  "No such field '%s' in object '%s'"
                      .formatted(variable, currentMapper.getBeanClass().getName()));
            }
            if (property.isReference()) {
              joinOn = prefix + "." + variable;
              prefix = prefix + "_" + variable;
              joins.put(joinOn, prefix);
              if (fetch) {
                fetches.add(joinOn);
              }
              return prefix;
            }
            if (translate && property.isTranslatable()) {
              return translate(property, prefix);
            }
          }
        }
      } else {
        Property property = mapper.getProperty(name);
        if (property == null) {
          throw new IllegalArgumentException(
              "No such field '%s' in object '%s'".formatted(variable, beanClass.getName()));
        }
        if (property.isCollection()) {
          return null;
        }
        if (property.getTarget() != null) {
          prefix = "_" + name;
          final String joinOn = "self." + name;
          joins.put(joinOn, prefix);
          if (fetch) {
            fetches.add(joinOn);
          }
          return prefix;
        }

        if (translate && property.isTranslatable()) {
          return translate(property, null);
        }
      }

      if (prefix == null) {
        prefix = "self";
      }

      return prefix + "." + variable;
    }

    private String getTranslationJoin(String joinName, String from, String variable, String lang) {
      return "MetaTranslation %s ON %s.key = CONCAT('value:', %s.%s) AND %s.language = '%s'"
          .formatted(joinName, joinName, from, variable, joinName, lang);
    }

    private String translate(Property property, String prefix) {
      final String variable = property.getName();
      final Locale locale = I18n.getBundle().getLocale();
      final String lang = locale.toLanguageTag();
      final String baseLang = locale.getLanguage();
      final String joinName =
          prefix == null
              ? "_meta_translation_%s".formatted(variable)
              : "_meta_translation%s_%s".formatted(prefix, variable);
      final String baseJoinName = joinName + "_base";
      final String from = prefix == null ? "self" : prefix;

      translationJoins.add(getTranslationJoin(joinName, from, variable, lang));
      translationJoins.add(getTranslationJoin(baseJoinName, from, variable, baseLang));

      return "COALESCE(NULLIF(%s.message, ''), NULLIF(%s.message, ''), %s.%s)"
          .formatted(joinName, baseJoinName, from, variable);
    }

    public String joinName(String name) {
      return joinName(name, false, false);
    }

    public String fixSelect(String query) {
      return hasCollection ? selectPattern.matcher(query).replaceFirst("$0DISTINCT ") : query;
    }

    @Override
    public String toString() {
      return toString(true);
    }

    public String toString(boolean fetch) {
      final List<String> joinItems = new ArrayList<>();
      for (final Entry<String, String> entry : joins.entrySet()) {
        final String fetchString = fetch && fetches.contains(entry.getKey()) ? " FETCH" : "";
        joinItems.add("LEFT JOIN%s %s %s".formatted(fetchString, entry.getKey(), entry.getValue()));
      }
      for (final String join : translationJoins) {
        joinItems.add("LEFT JOIN %s".formatted(join));
      }
      return joinItems.isEmpty() ? "" : " " + joinItems.stream().collect(Collectors.joining(" "));
    }
  }
}
