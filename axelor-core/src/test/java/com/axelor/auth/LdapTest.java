/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.axelor.JpaTestModule;
import com.axelor.TestingHelpers;
import com.axelor.app.AvailableAppSettings;
import com.axelor.auth.db.Group;
import com.axelor.auth.db.User;
import com.axelor.auth.db.repo.GroupRepository;
import com.axelor.auth.db.repo.UserRepository;
import com.axelor.auth.extensions.LdapExtension;
import com.axelor.auth.pac4j.AuthPac4jUserService;
import com.axelor.auth.pac4j.ldap.AxelorLdapProfileService;
import com.axelor.test.GuiceExtension;
import com.axelor.test.GuiceModules;
import com.google.inject.persist.Transactional;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.pac4j.core.credentials.UsernamePasswordCredentials;
import org.pac4j.core.exception.CredentialsException;
import org.pac4j.ldap.profile.LdapProfile;

@GuiceModules(LdapTest.LdapTestModule.class)
public class LdapTest {

  // Register LdapExtension before GuiceExtension in order to initialize ldap server before
  @Order(0)
  @RegisterExtension
  public static LdapExtension ldapExtension = new LdapExtension();

  @Order(1)
  @RegisterExtension
  public static GuiceExtension guiceExtension = new GuiceExtension();

  public static class LdapTestModule extends JpaTestModule {

    @Override
    protected void configure() {

      Map<String, String> properties = new HashMap<>();

      properties.put(
          AvailableAppSettings.AUTH_LDAP_SERVER_URL,
          "ldap://localhost:" + ldapExtension.getLdapPort());
      properties.put(AvailableAppSettings.AUTH_LDAP_SERVER_AUTH_USER, "uid=admin,ou=system");
      properties.put(AvailableAppSettings.AUTH_LDAP_SERVER_AUTH_PASSWORD, "secret");

      properties.put(AvailableAppSettings.AUTH_LDAP_GROUP_BASE, "ou=groups,dc=test,dc=com");
      properties.put(AvailableAppSettings.AUTH_LDAP_USER_BASE, "ou=users,dc=test,dc=com");

      properties.put(AvailableAppSettings.AUTH_LDAP_GROUP_FILTER, "(uniqueMember=uid={0})");
      properties.put(AvailableAppSettings.AUTH_LDAP_USER_FILTER, "(uid={0})");

      AxelorLdapProfileService ldap = new AxelorLdapProfileService(properties);
      bind(AxelorLdapProfileService.class).toInstance(ldap);

      super.configure();
    }
  }

  @SuppressWarnings("ConstantConditions")
  @AfterAll
  public static void tearDown() {
    TestingHelpers.logout();
    TestingHelpers.resetSettings();
  }

  @Inject private AxelorLdapProfileService authLdap;

  @Inject private AuthPac4jUserService userService;

  @Inject private UserRepository users;

  @Inject private GroupRepository groups;

  @Test
  @Transactional
  public void test() {

    Group admins = new Group("admins", "Administrators");

    groups.save(admins);

    // make sure there are no users
    ensureUsers(0);

    // attempt login with wrong password
    loginFailed();

    // it should not create user
    ensureUsers(0);

    // attempt login with good password
    loginSuccess();

    // it should create an user
    ensureUsers(1);

    // attempt login again with good password
    loginSuccess();

    // it should not create new user, as it's already created
    ensureUsers(1);

    // make sure groups exists on ldap server
    assertNotNull(authLdap.searchGroup("admins"));
    assertNotNull(authLdap.searchGroup("users"));
  }

  void ensureUsers(int count) {
    assertEquals(count, users.all().count());
  }

  void loginFailed() {
    try {
      authLdap.validate(null, new UsernamePasswordCredentials("jsmith", "Password"));
    } catch (CredentialsException e) {
      // ignore
    }
    User user = users.findByCode("jsmith");
    assertNull(user);
  }

  void loginSuccess() {
    LdapProfile p = authLdap.findById("jsmith");
    assertNotNull(p);

    authLdap.validate(null, new UsernamePasswordCredentials("jsmith", "password"));
    LdapProfile profile = authLdap.findById("jsmith");
    userService.saveUser(profile);

    User user = users.findByCode("jsmith");
    assertNotNull(user);
    assertEquals("John Smith", user.getName());
    assertNotNull(user.getGroup());
    assertEquals("admins", user.getGroup().getCode());
  }
}
