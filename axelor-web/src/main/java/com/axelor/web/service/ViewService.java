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
package com.axelor.web.service;

import com.axelor.auth.AuthUtils;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.JpaSecurity;
import com.axelor.db.JpaSecurity.AccessType;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.inject.Beans;
import com.axelor.meta.ActionExecutor;
import com.axelor.meta.ActionHandler;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.loader.XMLViews;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.schema.views.AbstractView;
import com.axelor.meta.schema.views.AbstractWidget;
import com.axelor.meta.schema.views.Button;
import com.axelor.meta.schema.views.ContainerView;
import com.axelor.meta.schema.views.Dashboard;
import com.axelor.meta.schema.views.Dashlet;
import com.axelor.meta.schema.views.Field;
import com.axelor.meta.schema.views.GridView;
import com.axelor.meta.schema.views.MenuItem;
import com.axelor.meta.schema.views.Panel;
import com.axelor.meta.schema.views.PanelField;
import com.axelor.meta.schema.views.PanelInclude;
import com.axelor.meta.schema.views.PanelRelated;
import com.axelor.meta.schema.views.PanelTabs;
import com.axelor.meta.schema.views.Search;
import com.axelor.meta.schema.views.SearchFilters;
import com.axelor.meta.schema.views.SimpleContainer;
import com.axelor.meta.schema.views.SimpleWidget;
import com.axelor.meta.service.MetaService;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.rpc.Request;
import com.axelor.rpc.Response;
import com.axelor.script.ScriptBindings;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.inject.Inject;
import com.google.inject.servlet.RequestScoped;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/meta")
public class ViewService extends AbstractService {

  @Inject private MetaService service;

  @Inject private JpaSecurity security;

  private Class<?> findClass(String name) {
    try {
      return Class.forName(name);
    } catch (Exception e) {
    }
    return null;
  }

  @GET
  @Path("models")
  @SuppressWarnings("all")
  @Tag(name = "Models")
  @Operation(
      summary = "Get list of all domain models",
      description = "This service returns list of all domain models.")
  public Response models() {

    final Response response = new Response();
    final List<String> all = Lists.newArrayList();

    for (Class<?> cls : JPA.models()) {
      if (security.isPermitted(AccessType.READ, (Class) cls)) {
        all.add(cls.getName());
      }
    }

    Collections.sort(all);

    response.setData(all);
    response.setTotal(all.size());
    response.setStatus(Response.STATUS_SUCCESS);
    return response;
  }

  @GET
  @Path("fields/{model}")
  @SuppressWarnings("all")
  @Tag(name = "Models")
  @Operation(
      summary = "Get properties of a domain model",
      description = "This service can be used to get properties of a domain model.")
  public Response fields(
      @PathParam("model") String model, @QueryParam("jsonModel") String jsonModel) {
    final Response response = new Response();
    final Map<String, Object> meta = Maps.newHashMap();
    final Class<?> modelClass = findClass(model);

    if (!security.isPermitted(AccessType.READ, (Class) modelClass)) {
      response.setStatus(Response.STATUS_FAILURE);
      return response;
    }

    final Map<String, Object> jsonFields = Maps.newHashMap();
    final List<String> names = Lists.newArrayList();

    meta.put("model", model);
    meta.put("jsonFields", jsonFields);

    if (StringUtils.isBlank(jsonModel)) {
      for (Property p : Mapper.of(modelClass).getProperties()) {
        if (!p.isTransient()) {
          names.add(p.getName());
        }
        if (p.isJson()) {
          jsonFields.put(p.getName(), MetaStore.findJsonFields(model, p.getName()));
        }
      }
      meta.putAll(MetaStore.findFields(modelClass, names));
    } else if (MetaJsonRecord.class.getName().equals(model)) {
      names.add("attrs");
      meta.putAll(MetaStore.findFields(modelClass, names));
      jsonFields.put("attrs", MetaStore.findJsonFields(jsonModel));
    }

    response.setData(meta);
    response.setStatus(Response.STATUS_SUCCESS);

    return response;
  }

  @GET
  @Path("views/{model}")
  @Hidden
  public Response views(@PathParam("model") String model) {
    final MultivaluedMap<String, String> params = getUriInfo().getQueryParameters(true);
    final Map<String, String> views = Maps.newHashMap();
    for (String mode : params.keySet()) {
      views.put(mode, params.getFirst(mode));
    }
    return service.findViews(findClass(model), views);
  }

  private Set<String> findNames(final Set<String> names, final AbstractWidget widget) {
    List<? extends AbstractWidget> all = null;
    if (widget instanceof SimpleContainer) {
      all = ((SimpleContainer) widget).getItems();
    } else if (widget instanceof Panel) {
      all = ((Panel) widget).getItems();
    } else if (widget instanceof PanelTabs) {
      all = ((PanelTabs) widget).getItems();
    } else if (widget instanceof PanelInclude) {
      names.addAll(findNames(((PanelInclude) widget).getView()));
    } else if (widget instanceof Field) {
      names.add(((Field) widget).getName());
      if (widget instanceof PanelField) {
        PanelField field = (PanelField) widget;
        if (field.getEditor() != null && field.getTarget() == null) {
          all = field.getEditor().getItems();
        }
        if (field.getViewer() != null && field.getTarget() == null) {
          String depends = field.getViewer().getDepends();
          if (StringUtils.notBlank(depends)) {
            Collections.addAll(names, depends.trim().split("\\s*,\\s*"));
          }
        }
      }
      // include related field for ref-select widget
      String relatedAttr = ((Field) widget).getRelated();
      if (StringUtils.notBlank(relatedAttr)) {
        names.add(relatedAttr);
      }
    } else if (widget instanceof PanelRelated) {
      names.add(((PanelRelated) widget).getName());
    }

    if (widget instanceof SimpleWidget) {
      String depends = ((SimpleWidget) widget).getDepends();
      if (StringUtils.notBlank(depends)) {
        Collections.addAll(names, depends.trim().split("\\s*,\\s*"));
      }
    }

    if (widget instanceof MenuItem) {
      String depends = ((MenuItem) widget).getDepends();
      if (StringUtils.notBlank(depends)) {
        Collections.addAll(names, depends.trim().split("\\s*,\\s*"));
      }
    }

    if (all == null) {
      return names;
    }
    for (AbstractWidget item : all) {
      findNames(names, item);
    }
    return names;
  }

  private Set<String> findNames(final AbstractView view) {
    final Set<String> names = new HashSet<>();
    final List<AbstractWidget> items = new ArrayList<>();

    if (view instanceof ContainerView) {
      final ContainerView containerView = (ContainerView) view;
      items.addAll(Optional.ofNullable(containerView.getItems()).orElse(Collections.emptyList()));
      items.addAll(containerView.getExtraItems());
      names.addAll(containerView.getExtraNames());
    }

    if (items.isEmpty()) {
      return names;
    }

    for (AbstractWidget widget : items) {
      findNames(names, widget);
    }

    return names;
  }

  @GET
  @Path("view")
  @Hidden
  public Response view(
      @QueryParam("model") String model,
      @QueryParam("name") String name,
      @QueryParam("type") String type,
      @QueryParam("jsonModel") String jsonModel) {

    final Response response = service.findView(model, name, type);
    final AbstractView view = (AbstractView) response.getData();

    final Map<String, Object> data = Maps.newHashMap();
    data.put("view", view);

    if (view instanceof Search && ((Search) view).getSearchForm() != null) {
      String searchForm = ((Search) view).getSearchForm();
      Response searchResponse = service.findView(null, searchForm, "form");
      data.put("searchForm", searchResponse.getData());
    }

    final Class<?> modelClass = findClass(model);
    if (view instanceof AbstractView && modelClass != null) {
      final Set<String> names = findNames(view);
      Mapper mapper = Mapper.of(modelClass);
      List<Property> jsonFields =
          names.stream()
              .map(i -> i.split("\\.")[0])
              .map(mapper::getProperty)
              .filter(Objects::nonNull)
              .filter(Property::isJson)
              .collect(Collectors.toList());
      if (ObjectUtils.isEmpty(jsonFields) && mapper.getProperty("attrs") != null) {
        jsonFields.add(mapper.getProperty("attrs"));
        names.add("attrs");
      }

      final Map<String, Object> jsonFieldsMap = Maps.newHashMap();
      for (Property jsonField : jsonFields) {
        Map<String, Object> jsonFieldMap =
            MetaJsonRecord.class.getName().equals(model) && ObjectUtils.notEmpty(jsonModel)
                ? MetaStore.findJsonFields(jsonModel)
                : MetaStore.findJsonFields(model, jsonField.getName());
        if (ObjectUtils.notEmpty(jsonFieldMap)) {
          jsonFieldsMap.put(jsonField.getName(), jsonFieldMap);
        }
      }
      if (ObjectUtils.notEmpty(jsonFieldsMap)) {
        data.put("jsonFields", jsonFieldsMap);
      }
      if (MetaJsonRecord.class.getName().equals(model)) {
        names.add("name");
      }
      data.putAll(MetaStore.findFields(modelClass, names));
    }

    response.setData(data);
    response.setStatus(Response.STATUS_SUCCESS);

    return response;
  }

  @POST
  @Path("view")
  @Hidden
  public Response view(Request request) {

    final Map<String, Object> data = request.getData();
    final String name = (String) data.get("name");
    final String type = (String) data.get("type");
    String jsonModel = null;
    try {
      jsonModel = (String) ((Map<?, ?>) data.get("context")).get("jsonModel");
    } catch (Exception e) {
      // ignore
    }

    return view(request.getModel(), name, type, jsonModel);
  }

  @POST
  @Path("view/fields")
  @Hidden
  public Response viewFields(Request request) {
    final Response response = new Response();
    response.setData(MetaStore.findFields(request.getBeanClass(), request.getFields()));
    return response;
  }

  @POST
  @Path("view/save")
  @Hidden
  public Response save(Request request) {
    final Map<String, Object> data = request.getData();
    try {
      final String type = (String) data.get("type");
      AbstractView view = null;
      switch (type) {
        case "dashboard":
          view = saveDashboard(data);
          break;
        case "grid":
          view = saveGridView(data);
          break;
      }
      if (view != null) {
        final Long customViewId =
            Optional.ofNullable(data.get("customViewId"))
                .map(id -> Long.parseLong(id.toString()))
                .orElse(null);
        return service.saveView(view, AuthUtils.getUser(), customViewId);
      }
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return null;
  }

  private AbstractView saveDashboard(Map<String, Object> json) {
    final Object viewId = json.get("viewId");
    final Object customViewId = json.get("customViewId");

    final ObjectMapper om = Beans.get(ObjectMapper.class);
    final Dashboard originalView =
        viewId != null ? (Dashboard) XMLViews.findView(Long.parseLong(viewId.toString())) : null;
    final Dashboard view =
        customViewId == null
            ? originalView
            : (Dashboard) XMLViews.findCustomView(Long.parseLong(customViewId.toString()));

    if (view == null) {
      throw new IllegalArgumentException("Trying to save invalid view schema.");
    }

    final List<AbstractWidget> items = new ArrayList<>();

    for (Object item : (List<?>) json.get("items")) {
      final Map<Object, Object> map = (Map<Object, Object>) item;

      try {
        items.add(om.readValue(om.writeValueAsString(map), Dashlet.class));
      } catch (IOException e) {
        throw new IllegalArgumentException("Trying to save invalid view schema.");
      }
    }

    view.setItems(items);

    return view;
  }

  private AbstractView saveGridView(Map<String, Object> json) {
    final Object viewId = json.get("viewId");
    final Object customViewId = json.get("customViewId");
    final String filterViewName = (String) json.get("filterViewName");

    if (viewId == null && customViewId == null) {
      return null;
    }

    final ObjectMapper om = Beans.get(ObjectMapper.class);
    final GridView originalView =
        viewId != null ? (GridView) XMLViews.findView(Long.parseLong(viewId.toString())) : null;
    final GridView view =
        customViewId == null
            ? originalView
            : (GridView) XMLViews.findCustomView(Long.parseLong(customViewId.toString()));
    final SearchFilters filterView =
        filterViewName != null
            ? (SearchFilters) XMLViews.findView(filterViewName, "search-filters")
            : null;

    final List<AbstractWidget> items = new ArrayList<>();
    final Set<String> names = new HashSet<>();

    for (AbstractWidget item : view.getItems()) {
      if (item instanceof PanelField || item instanceof Button) continue;
      items.add(item);
    }

    for (Object item : (List<?>) json.get("items")) {
      @SuppressWarnings("unchecked")
      final Map<Object, Object> map = (Map<Object, Object>) item;
      final String type = (String) map.get("type");
      if ("field".equals(type) || "button".equals(type)) {
        final Class<?> itemType = "field".equals(type) ? PanelField.class : Button.class;
        final String name = (String) map.get("name");
        if (StringUtils.notBlank(name)) {
          names.add(name);
        }

        final List<SimpleWidget> existing =
            view.getItems().stream()
                .filter(widget -> widget instanceof SimpleWidget)
                .map(SimpleWidget.class::cast)
                .filter(widget -> Objects.equals(widget.getName(), name))
                .collect(Collectors.toList());
        if (!existing.isEmpty()) {
          for (final SimpleWidget widget : existing) {
            widget.setHidden(null);
            final Object width = map.get("width");
            if (width != null) {
              widget.setWidth(String.valueOf(width));
            }
            items.add(widget);
          }
          continue;
        }

        // Retrieve original title
        if (map.containsKey("title")) {
          Stream<AbstractWidget> stream = view.getItems().stream();
          if (filterView != null) {
            stream = Stream.concat(stream, filterView.getItems().stream());
          }
          stream
              .filter(widget -> widget instanceof SimpleWidget)
              .map(SimpleWidget.class::cast)
              .filter(widget -> Objects.equals(widget.getName(), name))
              .filter(widget -> StringUtils.notBlank(widget.getTitle()))
              .findFirst()
              .ifPresent(widget -> map.put("title", widget.getTitle()));
        }

        try {
          items.add((AbstractWidget) om.readValue(om.writeValueAsString(map), itemType));
        } catch (IOException e) {
          // this should not happen
          throw new IllegalArgumentException("Trying to save invalid view schema.");
        }
      }
    }

    // Add missing fields as hidden
    if (originalView != null) {
      originalView.getItems().stream()
          .filter(widget -> widget instanceof SimpleWidget)
          .map(SimpleWidget.class::cast)
          .filter(widget -> StringUtils.notBlank(widget.getName()))
          .filter(widget -> !names.contains(widget.getName()))
          .forEach(
              widget -> {
                widget.setHidden(true);
                items.add(widget);
              });
    }

    view.setCustomViewShared((Boolean) json.get("customViewShared"));
    view.setItems(items);

    return view;
  }

  @GET
  @Path("chart/{name}")
  @Hidden
  public Response chart(@PathParam("name") String name) {
    final MultivaluedMap<String, String> params = getUriInfo().getQueryParameters(true);
    final Map<String, Object> context = Maps.newHashMap();
    final Request request = new Request();

    for (String key : params.keySet()) {
      List<String> values = params.get(key);
      if (values.size() == 1) {
        context.put(key, values.getFirst());
      } else {
        context.put(key, values);
      }
    }
    request.setData(context);

    return service.getChart(name, request);
  }

  @POST
  @Path("chart/{name}")
  @Hidden
  public Response chart(@PathParam("name") String name, Request request) {
    final Map<String, Object> data = request.getData();
    if (data == null || data.get("_domainAction") == null) {
      return service.getChart(name, request);
    }
    data.put("_model", ScriptBindings.class.getName());
    ViewService.updateContext((String) data.get("_domainAction"), data);
    return service.getChart(name, request);
  }

  @POST
  @Path("custom/{name}")
  @Hidden
  public Response dataset(@PathParam("name") String name, Request request) {
    final Map<String, Object> data = request.getData();
    if (data == null || data.get("_domainAction") == null) {
      return service.getDataSet(name, request);
    }
    data.put("_model", ScriptBindings.class.getName());
    ViewService.updateContext((String) data.get("_domainAction"), data);
    return service.getDataSet(name, request);
  }

  /**
   * Helper method to update context with re-evaluated domain context for the given action.
   *
   * @param action the action to re-evaluate
   * @param domainContext the context to update
   * @return updated domainContext
   */
  @SuppressWarnings("all")
  static Map<String, Object> updateContext(String action, Map<String, Object> domainContext) {
    if (action == null || domainContext == null) {
      return domainContext;
    }
    final Action act = MetaStore.getAction(action);
    if (act == null) {
      return domainContext;
    }

    final String model = (String) domainContext.get("_model");
    final ActionRequest actRequest = new ActionRequest();
    final Map<String, Object> actData = new HashMap<>();

    actData.put("_model", model);
    actData.put("_domainAction", action);
    actData.put("_domainContext", domainContext);

    actRequest.setModel(model);
    actRequest.setAction(action);
    actRequest.setData(actData);

    ActionExecutor executor = Beans.get(ActionExecutor.class);
    ActionHandler handler = executor.newActionHandler(actRequest);

    Object res = act.execute(handler);

    if (res instanceof ActionResponse) {
      res = ((ActionResponse) res).getItem(0);
      if (res instanceof Map && ((Map) res).containsKey("view")) {
        res = ((Map) res).get("view");
      }
    }

    if (res instanceof Map) {
      Map<String, Object> ctx = (Map) ((Map) res).get("context");
      if (ctx != null) {
        domainContext.putAll(ctx);
      }
    }

    return domainContext;
  }
}
