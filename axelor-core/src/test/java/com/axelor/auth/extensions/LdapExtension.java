/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.auth.extensions;

import com.axelor.common.ResourceUtils;
import com.unboundid.ldap.listener.Base64PasswordEncoderOutputFormatter;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.listener.UnsaltedMessageDigestInMemoryPasswordEncoder;
import com.unboundid.ldif.LDIFReader;
import java.io.InputStream;
import java.security.MessageDigest;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LdapExtension implements BeforeAllCallback, AfterAllCallback {

  private static final Logger LOG = LoggerFactory.getLogger(LdapExtension.class);

  private final String LDIF_FILE = "test.ldif";

  // By default, a random available port is chosen if not provided.
  private int ldapPort;
  private InMemoryDirectoryServer ldapServer;

  public LdapExtension() {}

  public LdapExtension(int port) {
    this.ldapPort = port;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    LOG.info("Starting LDAP server...");

    final MessageDigest sha1Digest = MessageDigest.getInstance("SHA-1");
    final InMemoryDirectoryServerConfig config =
        new InMemoryDirectoryServerConfig("dc=test,dc=com");

    config.addAdditionalBindCredentials("uid=admin,ou=system", "secret");
    config.setListenerConfigs(InMemoryListenerConfig.createLDAPConfig("LDAP", ldapPort));
    config.setEnforceSingleStructuralObjectClass(false);
    config.setEnforceAttributeSyntaxCompliance(true);
    config.setPasswordEncoders(
        new UnsaltedMessageDigestInMemoryPasswordEncoder(
            "{SHA}", Base64PasswordEncoderOutputFormatter.getInstance(), sha1Digest));

    ldapServer = new InMemoryDirectoryServer(config);
    try (InputStream is = ResourceUtils.getResourceStream(LDIF_FILE)) {
      ldapServer.importFromLDIF(false, new LDIFReader(is));
    }

    ldapServer.startListening();
    ldapPort = ldapServer.getListenPort();

    LOG.info("LDAP server started. Listen on port " + ldapPort);
  }

  @Override
  public void afterAll(ExtensionContext context) {
    LOG.info("Shutdown LDAP server...");

    ldapServer.close();
    ldapServer = null;
  }

  public int getLdapPort() {
    return ldapPort;
  }
}
