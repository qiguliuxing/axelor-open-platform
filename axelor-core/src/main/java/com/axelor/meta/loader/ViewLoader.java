/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.meta.loader;

import com.axelor.auth.db.Group;
import com.axelor.auth.db.repo.GroupRepository;
import com.axelor.common.FileUtils;
import com.axelor.common.Inflector;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.db.mapper.Property;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaScanner;
import com.axelor.meta.MetaStore;
import com.axelor.meta.db.MetaAction;
import com.axelor.meta.db.MetaActionMenu;
import com.axelor.meta.db.MetaMenu;
import com.axelor.meta.db.MetaSelect;
import com.axelor.meta.db.MetaSelectItem;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.db.repo.MetaActionMenuRepository;
import com.axelor.meta.db.repo.MetaActionRepository;
import com.axelor.meta.db.repo.MetaMenuRepository;
import com.axelor.meta.db.repo.MetaSelectRepository;
import com.axelor.meta.db.repo.MetaViewRepository;
import com.axelor.meta.schema.ObjectViews;
import com.axelor.meta.schema.actions.Action;
import com.axelor.meta.schema.actions.ActionView;
import com.axelor.meta.schema.views.AbstractView;
import com.axelor.meta.schema.views.AbstractWidget;
import com.axelor.meta.schema.views.ExtendableView;
import com.axelor.meta.schema.views.Field;
import com.axelor.meta.schema.views.FormView;
import com.axelor.meta.schema.views.GridView;
import com.axelor.meta.schema.views.MenuItem;
import com.axelor.meta.schema.views.Panel;
import com.axelor.meta.schema.views.PanelField;
import com.axelor.meta.schema.views.PanelRelated;
import com.axelor.meta.schema.views.Selection;
import com.axelor.meta.service.MetaService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.persistence.TypedQuery;
import jakarta.xml.bind.JAXBException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.xml.namespace.QName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewLoader extends AbstractParallelLoader {

  private static final Logger LOG = LoggerFactory.getLogger(ViewLoader.class);

  @Inject private ObjectMapper objectMapper;

  @Inject private MetaViewRepository views;

  @Inject private MetaSelectRepository selects;

  @Inject private MetaActionRepository actions;

  @Inject private MetaMenuRepository menus;

  @Inject private MetaActionMenuRepository actionMenus;

  @Inject private GroupRepository groups;

  @Inject private ViewGenerator viewGenerator;

  private final Set<String> computedViews = ConcurrentHashMap.newKeySet();
  private final Set<String> viewsToGenerate = ConcurrentHashMap.newKeySet();
  private final Map<String, List<String>> viewsToMigrate = new ConcurrentHashMap<>();
  private final Map<String, List<Consumer<Group>>> groupsToCreate = new ConcurrentHashMap<>();

  @Override
  protected void doLoad(URL file, Module module, boolean update) {
    LOG.debug("Importing: {}", file.getFile());

    try {
      process(file, module, update);
    } catch (IOException | JAXBException e) {
      LOG.error("Error while loading {}", file);
      throw new RuntimeException(e);
    }
  }

  @Override
  protected List<URL> findFiles(Module module) {
    return MetaScanner.findAll(module.getName(), "views", "(.*?)\\.xml");
  }

  @Override
  @Transactional
  protected void doLast(Module module, boolean update) {
    // generate default views
    importDefault(module);

    runResolveTasks();

    Set<?> unresolved = unresolvedKeys();
    if (!unresolved.isEmpty()) {
      LOG.error("Found {} unresolved item(s): {}", unresolved.size(), unresolved);
      throw new PersistenceException(
          "Found %d unresolved item(s). Please check the logs for details."
              .formatted(unresolved.size()));
    }

    migrateViews();
  }

  protected void initialize() {
    computedViews.addAll(
        JPA.em()
            .createQuery(
                "SELECT self.name FROM MetaView self WHERE self.computed = TRUE", String.class)
            .getResultList());
  }

  protected void terminate() {
    computedViews.clear();
    linkMissingGroups();
    generateFinalViews();

    final Set<String> duplicates = getDuplicates();
    if (!duplicates.isEmpty()) {
      LOG.error("Found {} duplicate item(s): {}", duplicates.size(), duplicates);
    }
  }

  private void migrateViews() {
    try {
      viewsToMigrate.forEach(
          (name, xmlIds) -> {
            final MetaView baseView = views.findByNameAndComputed(name, false);
            if (baseView != null) {
              views
                  .all()
                  .filter("self.xmlId IN :xmlIds")
                  .bind("xmlIds", xmlIds)
                  .update("priority", baseView.getPriority());
            }
          });
    } finally {
      viewsToMigrate.clear();
    }
  }

  @Transactional
  protected void linkMissingGroups() {
    if (ObjectUtils.isEmpty(groupsToCreate)) {
      return;
    }
    LOG.info("Creating missing groups...");
    try {
      groupsToCreate.forEach(
          (code, adders) -> {
            final Group existingGroup = groups.findByCode(code);
            final Group group;
            if (existingGroup != null) {
              LOG.debug("User group already created by data/demo: {}", code);
              group = existingGroup;
            } else {
              LOG.info("Creating a new user group: {}", code);
              group = groups.save(new Group(code, code));
            }
            adders.forEach(adder -> adder.accept(group));
          });
    } finally {
      groupsToCreate.clear();
    }
  }

  private void generateFinalViews() {
    if (viewsToGenerate.isEmpty()) {
      return;
    }

    LOG.info("Generating computed views...");

    try {
      viewGenerator.process(viewsToGenerate);
    } finally {
      viewsToGenerate.clear();
    }
  }

  private static <T> List<T> getList(List<T> list) {
    return list != null ? list : Collections.emptyList();
  }

  void process(URL url, Module module, boolean update) throws IOException, JAXBException {
    final ObjectViews all;

    try (InputStream stream = url.openStream()) {
      all = XMLViews.unmarshal(stream);
    }

    getList(all.getViews()).forEach(view -> importView(view, module, update));

    getList(all.getSelections()).forEach(selection -> importSelection(selection, module, update));

    getList(all.getActions())
        .forEach(
            action -> {
              importAction(action, module, update);
              MetaStore.invalidate(action.getName());
            });

    getList(all.getMenus()).forEach(item -> importMenu(item, module, update));

    getList(all.getActionMenus()).forEach(item -> importActionMenu(item, module, update));
  }

  private void importView(AbstractView view, Module module, boolean update) {
    importView(view, module, update, -1);
  }

  private void importView(AbstractView view, Module module, boolean update, int priority) {

    String xmlId = view.getXmlId();
    String name = view.getName();
    String type = view.getType();
    String modelName = view.getModel();

    if (isVisited(view.getClass(), name, AbstractView.class, xmlId)) {
      return;
    }

    if (view instanceof ExtendableView extendableView) {
      final boolean isExtension = Boolean.TRUE.equals(view.getExtension());

      if (isExtension || computedViews.contains(name)) {
        viewsToGenerate.add(name);
      }

      if (!isExtension && ObjectUtils.notEmpty(extendableView.getExtends())) {
        LOG.atError()
            .setMessage("View with extensions must have extension=\"true\": {}")
            .addArgument(() -> getName(name, xmlId))
            .log();
        return;
      }
    }

    LOG.atDebug().setMessage("Loading view: {}").addArgument(() -> getName(name, xmlId)).log();
    final String xml = XMLViews.toXml(view, true);

    if (type.matches("tree|chart|portal|dashboard|search|custom")) {
      modelName = null;
    } else if (StringUtils.isBlank(modelName)) {
      throw new IllegalArgumentException("Invalid view, model name missing.");
    }

    if (modelName != null) {
      Class<?> model;
      try {
        model = Class.forName(modelName);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException("Invalid view, model not found: " + modelName);
      }
      modelName = model.getName();
    }

    MetaView entity = views.findByID(xmlId);
    MetaView other = views.findByNameAndComputed(name, false);
    if (entity == null && StringUtils.isBlank(xmlId)) {
      entity =
          views
              .all()
              .filter(
                  """
                  self.name = ? AND self.module = ? \
                  AND self.xmlId IS NULL \
                  AND COALESCE(self.computed, FALSE) = FALSE""",
                  name,
                  module.getName())
              .fetchOne();
    }

    if (entity == null) {
      entity = new MetaView(name);
    }

    if (other == entity) {
      other = null;
    }

    if (Boolean.TRUE.equals(view.getExtension())) {
      if (!Boolean.TRUE.equals(entity.getExtension())) {
        // Migrated to extension view
        viewsToMigrate
            .computeIfAbsent(view.getName(), k -> Collections.synchronizedList(new ArrayList<>()))
            .add(view.getXmlId());
      }
    } else if (entity.getId() == null
        && other != null
        && !Objects.equals(xmlId, other.getXmlId())) {
      // Set priority higher than existing view
      entity.setPriority(other.getPriority() + 1);
    }

    if (entity.getId() != null && !update) {
      return;
    }

    if (priority > -1) {
      entity.setPriority(priority);
    }

    // delete personalized dashboards
    if ("dashboard".equals(type) && !xml.equals(entity.getXml())) {
      int deleted = Beans.get(MetaService.class).removeCustomViews(entity);
      if (deleted > 0) {
        LOG.info("{} custom views are deleted: {}", deleted, entity.getName());
      }
    }

    entity.setXmlId(xmlId);
    entity.setTitle(view.getTitle());
    entity.setType(type);
    entity.setModel(modelName);
    entity.setModule(module.getName());
    entity.setXml(xml);
    entity.setComputed(null);
    final Set<String> missingGroups = addGroups(entity::addGroup, view.getGroups());
    entity.setExtension(view.getExtension());

    if (entity.getTitle() == null) {
      entity.setTitle(name);
    }

    if (entity.getHelpLink() == null) {
      entity.setHelpLink(view.getHelpLink());
    }

    entity = views.save(entity);
    addToGroupsToCreate(entity, missingGroups);
  }

  private static String getName(String name, String xmlId) {
    return xmlId == null ? name : "%s(id=%s)".formatted(name, xmlId);
  }

  @Transactional
  protected void importSelection(Selection selection, Module module, boolean update) {

    String name = selection.getName();
    String xmlId = selection.getXmlId();

    if (isVisited(Selection.class, name, xmlId)) {
      return;
    }

    LOG.debug("Loading selection: {}", name);

    MetaSelect entity = selects.findByID(xmlId);
    MetaSelect other = selects.findByName(selection.getName());
    if (entity == null) {
      entity =
          selects
              .all()
              .filter("self.name = ? AND self.module = ?", name, module.getName())
              .fetchOne();
    }

    if (entity == null) {
      entity = new MetaSelect(selection.getName());
      entity.setXmlId(xmlId);
    }

    if (other == entity) {
      other = null;
    }

    // set priority higher to existing view
    if (entity.getId() == null && other != null && !Objects.equals(xmlId, other.getXmlId())) {
      entity.setPriority(other.getPriority() + 1);
    }

    if (entity.getId() != null && !update) {
      return;
    }

    entity.clearItems();
    entity.setModule(module.getName());

    int sequence = 0;
    for (Selection.Option opt : selection.getOptions()) {

      MetaSelectItem item = new MetaSelectItem();
      Integer seq = sequence++;

      if (opt.getOrder() != null) {
        seq = opt.getOrder();
      }

      item.setValue(opt.getValue());
      item.setTitle(opt.getTitle());
      item.setIcon(opt.getIcon());
      item.setColor(opt.getColor());
      item.setOrder(seq);
      item.setHidden(opt.getHidden());

      entity.addItem(item);
      if (opt.getDataAttributes() == null) {
        continue;
      }

      Map<String, Object> data = new HashMap<>();
      for (QName param : opt.getDataAttributes().keySet()) {
        String paramName = param.getLocalPart();
        if (paramName.startsWith("data-")) {
          data.put(paramName.substring(5), opt.getDataAttributes().get(param));
        }
      }
      try {
        item.setData(objectMapper.writeValueAsString(data));
      } catch (JsonProcessingException e) {
      }
    }

    selects.save(entity);
  }

  private Set<String> addGroups(Consumer<Group> adder, String codes) {
    final Set<String> missing = new HashSet<>();

    if (StringUtils.notBlank(codes)) {
      Arrays.stream(codes.split("\\s*,\\s*"))
          .forEach(
              code -> {
                final Group group = groups.findByCode(code);
                if (group != null) {
                  adder.accept(group);
                } else {
                  missing.add(code);
                }
              });
    }

    return missing;
  }

  private void addToGroupsToCreate(MetaView metaView, Set<String> codes) {
    final Long id = metaView.getId();
    codes.stream()
        .forEach(
            code ->
                groupsToCreate
                    .computeIfAbsent(code, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(
                        group -> {
                          final MetaView entity = views.find(id);
                          entity.addGroup(group);
                        }));
  }

  private void addToGroupsToCreate(MetaMenu metaMenu, Set<String> codes) {
    final Long id = metaMenu.getId();
    codes.stream()
        .forEach(
            code ->
                groupsToCreate
                    .computeIfAbsent(code, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(
                        group -> {
                          final MetaMenu entity = menus.find(id);
                          entity.addGroup(group);
                        }));
  }

  @Transactional
  protected void importAction(Action action, Module module, boolean update) {

    String name = action.getName();
    String xmlId = action.getXmlId();

    if (isVisited(Action.class, name, xmlId)) {
      return;
    }

    LOG.debug("Loading action: {}", name);

    MetaAction entity = actions.findByID(xmlId);
    MetaAction other = actions.findByName(name);
    if (entity == null) {
      entity =
          actions
              .all()
              .filter("self.name = ? AND self.module = ?", name, module.getName())
              .fetchOne();
    }

    if (entity == null) {
      entity = new MetaAction(name);
      entity.setXmlId(xmlId);
    }

    if (other == entity) {
      other = null;
    }

    // set priority higher to existing menu
    if (entity.getId() == null && other != null && !Objects.equals(xmlId, other.getXmlId())) {
      entity.setPriority(other.getPriority() + 1);
    }

    if (entity.getId() != null && !update) {
      return;
    }

    Class<?> klass = action.getClass();
    Mapper mapper = Mapper.of(klass);

    entity.setXml(XMLViews.toXml(action, true));

    String model = (String) mapper.get(action, "model");
    entity.setModel(model);
    entity.setModule(module.getName());

    String type = klass.getSimpleName().replaceAll("([a-z\\d])([A-Z]+)", "$1-$2").toLowerCase();
    entity.setType(type);

    if (action instanceof ActionView view) {
      Boolean home = view.getHome();
      if (home == null) {
        for (ActionView.View item : view.getViews()) {
          if ("dashboard".equals(item.getType())) {
            home = Boolean.TRUE;
            break;
          }
        }
      }
      entity.setHome(home);
    }

    entity = actions.save(entity);
    Long entityId = entity.getId();

    addResolveTask(MetaMenu.class, name, entityId, this::resolveActionOnMenu);

    addResolveTask(MetaActionMenu.class, name, entityId, this::resolveActionOnActionMenu);
  }

  private void resolveActionOnMenu(Long menuId, Long actionId) {
    MetaMenu pending = menus.find(menuId);
    LOG.debug("Resolved menu: {}", pending.getName());
    MetaAction actionEntity = actions.find(actionId);
    pending.setAction(actionEntity);
  }

  private void resolveActionOnActionMenu(Long menuId, Long actionId) {
    MetaActionMenu pending = actionMenus.find(menuId);
    LOG.debug("Resolved action menu: {}", pending.getName());
    MetaAction actionEntity = actions.find(actionId);
    pending.setAction(actionEntity);
  }

  @Transactional
  protected void importMenu(MenuItem menuItem, Module module, boolean update) {

    String name = menuItem.getName();
    String xmlId = menuItem.getXmlId();

    if (isVisited(MenuItem.class, name, xmlId)) {
      return;
    }

    LOG.debug("Loading menu: {}", name);

    MetaMenu entity = menus.findByID(xmlId);
    MetaMenu other = menus.findByName(name);
    if (entity == null) {
      entity =
          menus
              .all()
              .filter("self.name = ? AND self.module = ?", name, module.getName())
              .fetchOne();
    }

    if (entity == null) {
      entity = new MetaMenu(name);
      entity.setXmlId(xmlId);
    }

    if (other == entity) {
      other = null;
    }

    // set priority higher to existing menu
    if (entity.getId() == null && other != null && !Objects.equals(xmlId, other.getXmlId())) {
      entity.setPriority(other.getPriority() + 1);
    }

    if (entity.getId() != null && !update) {
      return;
    }

    entity.setTitle(menuItem.getTitle());
    entity.setIcon(menuItem.getIcon());
    entity.setIconBackground(menuItem.getIconBackground());
    entity.setModule(module.getName());
    entity.setTag(menuItem.getTag());
    entity.setTagGet(menuItem.getTagGet());
    entity.setTagCount(menuItem.getTagCount());
    entity.setTagStyle(menuItem.getTagStyle());
    entity.setLeft(menuItem.getLeft() == null ? true : menuItem.getLeft());
    entity.setMobile(menuItem.getMobile());
    entity.setHidden(menuItem.getHidden());
    final Set<String> missingGroups = addGroups(entity::addGroup, menuItem.getGroups());

    entity.setConditionToCheck(menuItem.getConditionToCheck());
    entity.setModuleToCheck(menuItem.getModuleToCheck());

    if (menuItem.getOrder() != null) {
      entity.setOrder(menuItem.getOrder());
    }

    entity = menus.save(entity);

    if (!Strings.isNullOrEmpty(menuItem.getParent())) {
      MetaMenu parent = menus.findByName(menuItem.getParent());
      if (parent == null) {
        LOG.debug("Unresolved parent: {}", menuItem.getParent());
        this.setUnresolved(MetaMenu.class, menuItem.getParent(), entity.getId());
      } else {
        entity.setParent(parent);
      }
    }

    if (!StringUtils.isBlank(menuItem.getAction())) {
      MetaAction action = actions.findByName(menuItem.getAction());
      if (action == null) {
        LOG.debug("Unresolved action: {}", menuItem.getAction());
        setUnresolved(MetaMenu.class, menuItem.getAction(), entity.getId());
      } else {
        entity.setAction(action);
      }
    }

    Long entityId = entity.getId();

    addResolveTask(MetaMenu.class, name, entityId, this::resolveParentOnMenu);
    addToGroupsToCreate(entity, missingGroups);
  }

  private void resolveParentOnMenu(Long menuId, Long parentMenuId) {
    MetaMenu pending = menus.find(menuId);
    LOG.debug("Resolved menu: {}", pending.getName());
    MetaMenu metaMenuEntity = menus.find(parentMenuId);
    pending.setParent(metaMenuEntity);
  }

  @Transactional
  protected void importActionMenu(MenuItem menuItem, Module module, boolean update) {
    String name = menuItem.getName();
    String xmlId = menuItem.getXmlId();

    if (isVisited(MenuItem.class, name, xmlId)) {
      return;
    }

    LOG.debug("Loading action menu: {}", name);

    MetaActionMenu entity = actionMenus.findByID(xmlId);
    MetaActionMenu other = actionMenus.findByName(name);
    if (entity == null) {
      entity =
          actionMenus
              .all()
              .filter("self.name = ? AND self.module = ?", name, module.getName())
              .fetchOne();
    }

    if (entity == null) {
      entity = new MetaActionMenu(name);
      entity.setXmlId(xmlId);
    }

    if (other == entity) {
      other = null;
    }

    // set priority higher to existing menu
    if (entity.getId() == null && other != null && !Objects.equals(xmlId, other.getXmlId())) {
      entity.setPriority(other.getPriority() + 1);
    }

    if (entity.getId() != null && !update) {
      return;
    }

    entity.setTitle(menuItem.getTitle());
    entity.setModule(module.getName());
    entity.setCategory(menuItem.getCategory());

    if (menuItem.getOrder() != null) {
      entity.setOrder(menuItem.getOrder());
    }

    entity = actionMenus.save(entity);

    if (!Strings.isNullOrEmpty(menuItem.getParent())) {
      MetaActionMenu parent = actionMenus.findByName(menuItem.getParent());
      if (parent == null) {
        LOG.debug("Unresolved parent: {}", menuItem.getParent());
        this.setUnresolved(MetaActionMenu.class, menuItem.getParent(), entity.getId());
      } else {
        entity.setParent(parent);
      }
    }

    if (!StringUtils.isBlank(menuItem.getAction())) {
      MetaAction action = actions.findByName(menuItem.getAction());
      if (action == null) {
        LOG.debug("Unresolved action: {}", menuItem.getAction());
        setUnresolved(MetaActionMenu.class, menuItem.getAction(), entity.getId());
      } else {
        entity.setAction(action);
      }
    }

    Long entityId = entity.getId();

    addResolveTask(MetaActionMenu.class, name, entityId, this::resolveParentOnActionMenu);
  }

  private void resolveParentOnActionMenu(Long actionMenuId, Long parentActionMenuId) {
    MetaActionMenu pending = actionMenus.find(actionMenuId);
    LOG.debug("Resolved action menu: {}", pending.getName());
    MetaActionMenu metaActionMenuEntity = actionMenus.find(parentActionMenuId);
    pending.setParent(metaActionMenuEntity);
  }

  private static final File outputDir =
      FileUtils.getFile(System.getProperty("java.io.tmpdir"), "axelor", "generated");

  private void importDefault(Module module) {
    final List<String> names = new ArrayList<>();
    for (String name : ModelLoader.findEntities(module)) {
      final Class<?> klass = JPA.model(name);
      if (klass != null) {
        names.add(klass.getName());
      }
    }
    if (names.isEmpty()) {
      return;
    }

    final TypedQuery<String> query =
        JPA.em().createQuery("SELECT s.model FROM MetaView s", String.class);
    final List<String> found = query.getResultList();
    for (String name : names) {
      if (found.contains(name)) continue;
      final Class<?> klass = JPA.model(name);
      final File out = FileUtils.getFile(outputDir, "views", klass.getSimpleName() + ".xml");
      final String xml = createDefaults(module, klass);
      try {
        LOG.debug("Creating default views: {}", out);
        Files.createParentDirs(out);
        Files.asCharSink(out, StandardCharsets.UTF_8).write(xml);
      } catch (IOException e) {
        LOG.error("Unable to create: {}", out);
      }
    }
  }

  private String createDefaults(Module module, final Class<?> klass) {
    List<AbstractView> views = createDefaults(klass);
    for (AbstractView view : views) {
      importView(view, module, false, 10);
    }
    return XMLViews.toXml(views, false);
  }

  List<AbstractView> createDefaults(final Class<?> klass) {

    final List<AbstractView> all = new ArrayList<>();
    final FormView formView = new FormView();
    final GridView gridView = new GridView();

    final Inflector inflector = Inflector.getInstance();

    String viewName = inflector.underscore(klass.getSimpleName());
    String viewTitle = klass.getSimpleName();

    viewName = inflector.dasherize(viewName);
    viewTitle = inflector.humanize(viewTitle);

    formView.setName(viewName + "-form");
    gridView.setName(viewName + "-grid");

    formView.setModel(klass.getName());
    gridView.setModel(klass.getName());

    formView.setTitle(viewTitle);
    gridView.setTitle(inflector.pluralize(viewTitle));

    List<AbstractWidget> formItems = new ArrayList<>();
    List<AbstractWidget> gridItems = new ArrayList<>();
    List<AbstractWidget> related = new ArrayList<>();

    Mapper mapper = Mapper.of(klass);
    List<String> fields = Lists.reverse(fieldNames(klass));

    for (String n : fields) {
      Property p = mapper.getProperty(n);
      if (p == null || p.isPrimary() || p.isVersion() || "cid".equals(p.getName())) {
        continue;
      }
      if (p.isCollection()) {
        if (p.getTargetName() == null) {
          continue;
        }
        PanelRelated panel = new PanelRelated();
        List<AbstractWidget> items = new ArrayList<>();
        Field item = new PanelField();
        item.setName(p.getTargetName());
        items.add(item);
        panel.setName(p.getName());
        panel.setTarget(p.getTarget().getName());
        panel.setItems(items);
        related.add(panel);
      } else {
        Field formItem = new PanelField();
        Field gridItem = new PanelField();
        formItem.setName(p.getName());
        gridItem.setName(p.getName());
        formItems.add(formItem);
        gridItems.add(gridItem);
      }
    }

    Panel overview = new Panel();
    overview.setTitle("Overview");
    overview.setItems(formItems);

    formItems = new ArrayList<>();
    formItems.add(overview);
    formItems.addAll(related);

    formView.setItems(formItems);
    gridView.setItems(gridItems);

    all.add(gridView);
    all.add(formView);

    return all;
  }

  // Fields names are not in ordered but some JVM implementation can.
  private List<String> fieldNames(Class<?> klass) {
    List<String> result = new ArrayList<>();
    for (java.lang.reflect.Field field : klass.getDeclaredFields()) {
      if (!field.getName().matches("id|version|selected|created(By|On)|updated(By|On)")) {
        result.add(field.getName());
      }
    }
    if (klass.getSuperclass() != Object.class) {
      result.addAll(fieldNames(klass.getSuperclass()));
    }
    return Lists.reverse(result);
  }
}
