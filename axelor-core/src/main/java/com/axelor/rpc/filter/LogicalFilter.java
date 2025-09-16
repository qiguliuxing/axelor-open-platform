/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.rpc.filter;

import com.axelor.common.StringUtils;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

class LogicalFilter extends Filter {

  private Operator operator;

  private List<Filter> filters;

  public LogicalFilter(Operator operator, List<Filter> filters) {
    this.operator = operator;
    this.filters = filters;
  }

  @Override
  public <T extends Model> Query<T> build(Class<T> klass) {
    return new LogicalFilterQuery<>(klass).filter();
  }

  @Override
  public String getQuery() {
    if (filters == null || filters.isEmpty()) return "";

    final List<String> filterParts =
        filters.stream()
            .map(Filter::toString)
            .filter(StringUtils::notBlank)
            .collect(Collectors.toList());

    StringBuilder sb = new StringBuilder();

    if (operator == Operator.NOT) sb.append("NOT ");

    if (filterParts.size() > 1) sb.append("(");

    String joiner = operator == Operator.NOT ? " AND " : " " + operator.name() + " ";
    sb.append(Joiner.on(joiner).join(filterParts));

    if (filterParts.size() > 1) sb.append(")");

    return sb.toString();
  }

  @Override
  public List<Object> getParams() {
    List<Object> params = new ArrayList<>();
    for (Filter filter : filters) {
      params.addAll(filter.getParams());
    }
    return params;
  }

  protected class LogicalFilterQuery<T extends Model> extends Query<T> {

    public LogicalFilterQuery(Class<T> beanClass) {
      super(beanClass);
    }

    public Query<T> filter() {
      final UnaryOperator<String> queryTransform;
      final Operator joinOperator;

      if (Operator.NOT.equals(operator)) {
        queryTransform = query -> Operator.NOT.name() + ' ' + query;
        joinOperator = Operator.AND;
      } else {
        queryTransform = UnaryOperator.identity();
        joinOperator = operator;
      }

      final String filterString =
          filters.stream()
              .filter(filterItem -> StringUtils.notBlank(filterItem.toString()))
              .map(
                  filterItem ->
                      getJoinHelper()
                          .parse(
                              queryTransform.apply(filterItem.toString()),
                              LogicalFilter.this.isTranslate() || filterItem.isTranslate()))
              .collect(Collectors.joining(' ' + joinOperator.name() + ' '));

      if (StringUtils.notBlank(filterString)) {
        setFilter(fixPlaceholders(filterString));
        setParams(LogicalFilter.this.getParams().toArray());
      }

      return this;
    }
  }
}
