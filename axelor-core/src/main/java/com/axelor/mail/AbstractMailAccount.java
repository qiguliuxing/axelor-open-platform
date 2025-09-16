/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.mail;

import jakarta.mail.Authenticator;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import java.util.Properties;

public abstract class AbstractMailAccount implements MailAccount {

  private String host;
  private String port;
  private String user;
  private String password;
  private String channel;
  private String protocol;

  private int connectionTimeout = MailConstants.DEFAULT_TIMEOUT;
  private int timeout = MailConstants.DEFAULT_TIMEOUT;

  private Properties properties;

  private Session session;

  /**
   * Create a new mail account.
   *
   * @param protocol server account protocol
   * @param host server hostname
   * @param port server port
   * @param user login name
   * @param password login password
   * @param channel encryption channel (ssl, starttls or null)
   */
  public AbstractMailAccount(
      String protocol, String host, String port, String user, String password, String channel) {
    this.host = host;
    this.port = port;
    this.user = user;
    this.password = password;
    this.channel = channel;
    this.protocol = protocol;
  }

  private Session init() {

    final Properties props = new Properties();

    // set timeout
    props.setProperty("mail." + protocol + ".connectiontimeout", "" + connectionTimeout);
    props.setProperty("mail." + protocol + ".timeout", "" + timeout);

    props.setProperty("mail.store.protocol", protocol);
    props.setProperty("mail." + protocol + ".host", host);
    props.setProperty("mail." + protocol + ".port", port);

    if (MailConstants.CHANNEL_STARTTLS.equalsIgnoreCase(channel)) {
      props.setProperty("mail." + protocol + ".starttls.enable", "true");
    }
    if (MailConstants.CHANNEL_SSL.equalsIgnoreCase(channel)) {
      props.setProperty("mail." + protocol + ".ssl.enable", "true");
      props.setProperty("mail." + protocol + ".socketFactory.port", port);
      props.setProperty(
          "mail." + protocol + ".socketFactory.class", "javax.net.ssl.SSLSocketFactory");
    }

    final Authenticator authenticator =
        new Authenticator() {

          @Override
          protected PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(user, password);
          }
        };

    if (properties != null) {
      props.putAll(properties);
    }

    return Session.getInstance(props, authenticator);
  }

  @Override
  public void setConnectionTimeout(int connectionTimeout) {
    this.connectionTimeout = connectionTimeout;
  }

  @Override
  public void setTimeout(int timeout) {
    this.timeout = timeout;
  }

  @Override
  public void setProperties(Properties properties) {
    this.properties = new Properties(properties);
  }

  @Override
  public Session getSession() {
    if (session == null) {
      session = init();
    }
    return session;
  }
}
