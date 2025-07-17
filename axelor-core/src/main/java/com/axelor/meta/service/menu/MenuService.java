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
package com.axelor.meta.service.menu;

import com.axelor.app.internal.AppFilter;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.JPA;
import com.axelor.meta.db.MetaMenu;
import com.axelor.meta.schema.views.MenuItem;
import com.axelor.meta.service.tags.TagsService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.inject.Inject;
import jakarta.persistence.TypedQuery;
import org.hibernate.jpa.QueryHints;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MenuService {

  private static final Logger LOG = LoggerFactory.getLogger(MenuService.class);

  @Inject private TagsService tagsService;

  /**
   * Get menus for the given user
   *
   * @param user the given user
   * @return list of {@link MenuItem}
   */
  public List<MenuItem> getMenus(User user) {

    if (user == null) {
      return Collections.emptyList();
    }

    // fetch meta menu
    final List<MetaMenu> metaMenus = MenuUtils.fetchMetaMenu(null);
    if (ObjectUtils.isEmpty(metaMenus)) {
      return Collections.emptyList();
    }

    final Map<String, String> helps = new HashMap<>();
    List<MenuItem> menuItems = new ArrayList<>();
    MenuChecker checker = new MenuChecker(metaMenus, user);

    // pre-build menu helps dictionary
    if (!Boolean.TRUE.equals(user.getNoHelp())) {
      helps.putAll(getHelps());
    }

    // build tree
    MenuNode rootNode = MenuNode.buildTree(metaMenus);

    // traverse tree
    rootNode.traverse(
        new SimpleMenuNodeVisitor() {

          /** Check whether the node can be visited */
          @Override
          public MenuNodeResult preChildVisit(MenuNode childNode) {
            try {
              if (checker.isAllowed(childNode.getMetaMenu())
                  && checker.canShow(childNode.getMetaMenu())) {
                return MenuNodeResult.CONTINUE;
              }
            } catch (Exception e) {
              LOG.error(
                  "Unable to evaluate menu {} : {}",
                  childNode.getMetaMenu().getName(),
                  e.getMessage());
            }
            return MenuNodeResult.TERMINATE;
          }

          /** On each node visited */
          @Override
          public MenuNodeResult visit(MenuNode node) {
            if (!node.isRoot()) {
              menuItems.add(buildMenuItem(node.getMetaMenu(), helps));
            }
            return MenuNodeResult.CONTINUE;
          }
        });

    menuItems.sort(new MenuItemComparator());
    return menuItems;
  }

  private Map<String, String> getHelps() {
    final Locale locale = AppFilter.getLocale();
    final String lang = locale.toLanguageTag();
    final String baseLang = locale.getLanguage();

    final TypedQuery<Object[]> query =
        JPA.em()
            .createQuery(
                "SELECT self.menu, MAX(CASE WHEN self.language = :lang THEN self.help ELSE base.help END) "
                    + "FROM MetaHelp self "
                    + "LEFT JOIN MetaHelp base ON base.menu = self.menu AND base.language = :baseLang "
                    + "WHERE self.menu IS NOT NULL AND self.language IN (:lang, :baseLang) "
                    + "GROUP BY self.menu",
                Object[].class)
            .setParameter("lang", lang)
            .setParameter("baseLang", baseLang)
            .setHint(QueryHints.HINT_CACHEABLE, true);

    return query.getResultList().stream()
        .collect(Collectors.toMap(a -> (String) a[0], a -> (String) a[1]));
  }

  /**
   * Create {@link MenuItem} from {@link MetaMenu}
   *
   * @param menu the {@link MetaMenu}
   * @param helps helps dictionary
   * @return created {@link MenuItem}
   */
  private MenuItem buildMenuItem(MetaMenu menu, Map<String, String> helps) {

    MenuItem item = new MenuItem();
    item.setName(menu.getName());
    item.setOrder(menu.getOrder());
    item.setTitle(menu.getTitle());
    item.setIcon(menu.getIcon());
    item.setIconBackground(menu.getIconBackground());
    item.setHasTag(menu.getTagCount() || StringUtils.notEmpty(menu.getTagGet()));
    item.setTagStyle(menu.getTagStyle());
    item.setLeft(menu.getLeft());
    item.setMobile(menu.getMobile());
    item.setHidden(menu.getHidden());
    item.setModuleToCheck(menu.getModuleToCheck());
    item.setConditionToCheck(menu.getConditionToCheck());

    if (helps.containsKey(menu.getName())) {
      item.setHelp(helps.get(menu.getName()));
    }

    if (menu.getParent() != null) {
      item.setParent(menu.getParent().getName());
    }

    if (menu.getAction() != null) {
      item.setAction(menu.getAction().getName());
    }

    item.setTag(tagsService.getTagValue(menu));

    return item;
  }
}
