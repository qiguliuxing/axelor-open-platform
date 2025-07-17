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
package com.axelor.mail.db.repo;

import com.axelor.auth.AuthUtils;
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.JPA;
import com.axelor.db.JpaRepository;
import com.axelor.db.Model;
import com.axelor.db.mapper.Mapper;
import com.axelor.i18n.I18n;
import com.axelor.inject.Beans;
import com.axelor.mail.MailConstants;
import com.axelor.mail.MailException;
import com.axelor.mail.db.MailAddress;
import com.axelor.mail.db.MailFlags;
import com.axelor.mail.db.MailMessage;
import com.axelor.mail.service.MailService;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.axelor.rpc.Resource;
import com.google.common.base.Objects;
import com.google.inject.persist.Transactional;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;
import jakarta.mail.internet.InternetAddress;
import jakarta.persistence.PersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MailMessageRepository extends JpaRepository<MailMessage> {

  @Inject private MetaFiles metaFiles;

  @Inject private MailService mailService;

  @Inject private MetaAttachmentRepository attachmentRepo;

  @Inject private MailFlagsRepository flagsRepo;

  private Logger log = LoggerFactory.getLogger(MailMessageRepository.class);

  public MailMessageRepository() {
    super(MailMessage.class);
  }

  public List<MailMessage> findAll(Model related, int limit, int offset) {
    return all()
        .filter(
            "self.relatedModel = ? AND self.relatedId = ?",
            EntityHelper.getEntityClass(related).getName(),
            related.getId())
        .order("-createdOn")
        .fetch(limit, offset);
  }

  public List<MailMessage> findBy(String type, Model related, int limit, int offset) {
    if (StringUtils.isBlank(type)) return findAll(related, limit, offset);
    return all()
        .filter(
            "self.relatedModel = ? AND self.relatedId = ? AND self.type = ?",
            EntityHelper.getEntityClass(related).getName(),
            related.getId(),
            type)
        .order("-createdOn")
        .fetch(limit, offset);
  }

  public long count(Model related) {
    return all()
        .filter(
            "self.relatedModel = ? AND self.relatedId = ?",
            EntityHelper.getEntityClass(related).getName(),
            related.getId())
        .count();
  }

  public long countBy(String type, Model related) {
    if (StringUtils.isBlank(type)) return count(related);
    return all()
        .filter(
            "self.relatedModel = ? AND self.relatedId = ? AND self.type = ?",
            EntityHelper.getEntityClass(related).getName(),
            related.getId(),
            type)
        .count();
  }

  public Model findRelated(MailMessage message) {
    if (message.getRelatedId() == null) return null;
    try {
      Class<?> klass = Class.forName(message.getRelatedModel());
      return (Model) JPA.em().find(klass, message.getRelatedId());
    } catch (Exception e) {
      return null;
    }
  }

  @Override
  public void remove(MailMessage message) {
    // delete all attachments
    List<MetaAttachment> attachments =
        attachmentRepo
            .all()
            .filter(
                "self.objectId = ? AND self.objectName = ?",
                message.getId(),
                MailMessage.class.getName())
            .fetch();

    for (MetaAttachment attachment : attachments) {
      try {
        metaFiles.delete(attachment);
      } catch (IOException e) {
        throw new PersistenceException(e);
      }
    }
    super.remove(message);
  }

  private static String mailHost;
  private static AtomicInteger mailId = new AtomicInteger();

  protected String generateMessageId(MailMessage entity) {
    if (mailHost == null) {
      final InternetAddress addr = InternetAddress.getLocalAddress(null);
      mailHost = addr == null ? "javamailuser@localhost" : addr.getAddress();
      if (mailHost.indexOf("@") > 0) {
        mailHost = mailHost.substring(mailHost.lastIndexOf("@"));
      }
    }
    final StringBuilder builder = new StringBuilder();
    builder.append("<");
    builder.append(builder.hashCode()).append(".");
    builder.append(mailId.getAndIncrement()).append(".");
    builder.append(System.currentTimeMillis());
    builder.append(mailHost);
    builder.append(">");
    return builder.toString();
  }

  @Override
  public MailMessage save(MailMessage entity) {
    if (entity.getParent() == null && entity.getRelatedId() != null) {
      MailMessage parent =
          all()
              .filter("self.parent is null AND self.relatedId = :id AND self.relatedModel = :model")
              .bind("id", entity.getRelatedId())
              .bind("model", entity.getRelatedModel())
              .order("id")
              .cacheable()
              .autoFlush(false)
              .fetchOne();
      entity.setParent(parent);
    }

    MailMessage root = entity.getRoot();
    if (root == null) {
      root = entity.getParent();
    }
    if (root != null && root.getRoot() != null) {
      root = root.getRoot();
    }
    entity.setRoot(root);

    // mark root as unread
    if (root != null && AuthUtils.getUser() != null) {
      flagsRepo
          .all()
          .filter("self.message.id = :mid and self.user.id != :uid")
          .bind("mid", root.getId())
          .bind("uid", AuthUtils.getUser().getId())
          .update("isRead", false);
    }

    boolean isNew = entity.getId() == null;
    boolean isNotification = MailConstants.MESSAGE_TYPE_NOTIFICATION.equals(entity.getType());

    // make sure to set unique message-id
    if (entity.getMessageId() == null) {
      entity.setMessageId(generateMessageId(entity));
    }

    final MailMessage saved = super.save(entity);

    // notify all followers by email
    if (isNotification && isNew) {
      email(saved);
    }

    return saved;
  }

  public void email(MailMessage message) {
    try {
      mailService.send(message);
    } catch (MailException e) {
      log.error("Error sending mail: " + e.getMessage(), e);
    }
  }

  @Transactional
  public MailMessage post(Model entity, MailMessage message, List<MetaFile> files) {

    final Mapper mapper = Mapper.of(EntityHelper.getEntityClass(entity));
    final MailFlags flags = new MailFlags();

    message.setRelatedId(entity.getId());
    message.setRelatedModel(EntityHelper.getEntityClass(entity).getName());
    message.setAuthor(AuthUtils.getUser());

    // mark message as read
    flags.setMessage(message);
    flags.setUser(AuthUtils.getUser());
    flags.setIsRead(Boolean.TRUE);
    message.addFlag(flags);

    try {
      message.setRelatedName(mapper.getNameField().get(entity).toString());
    } catch (Exception e) {
    }

    if (message.getRecipients() != null) {
      final Set<MailAddress> recipients = new HashSet<>();
      final MailAddressRepository addresses = Beans.get(MailAddressRepository.class);
      for (MailAddress address : message.getRecipients()) {
        recipients.add(addresses.findOrCreate(address.getAddress(), address.getPersonal()));
      }
      message.clearRecipients();
      message.setRecipients(recipients);
    }

    message = save(message);

    // Attach files if any
    if (ObjectUtils.notEmpty(files)) {

      for (MetaFile file : files) {
        MetaAttachment attachment = new MetaAttachment();

        attachment.setObjectId(message.getId());
        attachment.setObjectName(message.getClass().getName());
        attachment.setMetaFile(file);

        attachmentRepo.save(attachment);
      }
    }

    // notify all followers by email
    email(message);

    return message;
  }

  public List<MetaAttachment> findAttachments(MailMessage message) {
    if (message == null || message.getId() == null) {
      return new ArrayList<>();
    }

    return attachmentRepo
        .all()
        .filter(
            "self.objectId = ? AND self.objectName = ?",
            message.getId(),
            EntityHelper.getEntityClass(message).getName())
        .fetch();
  }

  public Map<String, Object> details(MailMessage message) {
    final String[] fields = {
      "id", "type", "subject", "body", "summary", "relatedId", "relatedModel", "relatedName"
    };
    final Map<String, Object> details = Resource.toMap(message, fields);
    final List<Object> files = new ArrayList<>();

    final MailFlags flags = flagsRepo.findBy(message, AuthUtils.getUser());
    final List<MetaAttachment> attachments = findAttachments(message);

    for (MetaAttachment attachment : attachments) {
      final Map<String, Object> fileInfo = Resource.toMapCompact(attachment.getMetaFile());
      fileInfo.put("fileIcon", metaFiles.fileTypeIcon(attachment.getMetaFile()));
      files.add(fileInfo);
    }

    if (flags != null) {
      details.put("$flags", Resource.toMap(flags, "isRead", "isStarred", "isArchived"));
    }

    String eventType = message.getType();
    String eventText = I18n.get("updated document");
    if (MailConstants.MESSAGE_TYPE_COMMENT.equals(eventType)
        || MailConstants.MESSAGE_TYPE_EMAIL.equals(eventType)) {
      eventText = I18n.get("added comment");
      details.put("$canDelete", Objects.equal(message.getCreatedBy(), AuthUtils.getUser()));
    }

    final MailAddress email = message.getFrom();
    final User user = message.getAuthor();

    Model author = user;
    if (author == null && email != null) {
      author = mailService.resolve(email.getAddress());
    }

    if (author != null) {
      final String authorModel = EntityHelper.getEntityClass(author).getName();
      details.put("$authorModel", authorModel);
    }

    if (user != null && user.getImage() != null) {
      details.put(
          "$avatar",
          "ws/rest/"
              + User.class.getName()
              + "/"
              + user.getId()
              + "/image/download?image=true&v="
              + user.getVersion());
    }

    details.put("$from", Resource.toMap(email, "address", "personal"));
    details.put("$author", Resource.toMapCompact(author));
    details.put("$files", files);
    details.put("$eventType", eventType);
    details.put("$eventText", eventText);
    details.put("$eventTime", message.getCreatedOn());

    return details;
  }
}
