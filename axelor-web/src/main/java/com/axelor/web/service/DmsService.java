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
import com.axelor.auth.db.User;
import com.axelor.common.ObjectUtils;
import com.axelor.common.StringUtils;
import com.axelor.common.csv.CSVFile;
import com.axelor.common.http.ContentDisposition;
import com.axelor.db.JPA;
import com.axelor.db.JpaSecurity;
import com.axelor.db.Model;
import com.axelor.db.Query;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.file.store.FileStoreFactory;
import com.axelor.file.temp.TempFiles;
import com.axelor.inject.Beans;
import com.axelor.meta.MetaFiles;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.axelor.rpc.Request;
import com.axelor.rpc.Resource;
import com.axelor.rpc.Response;
import com.axelor.script.GroovyScriptHelper;
import com.axelor.script.ScriptHelper;
import com.google.common.collect.ImmutableMap;
import com.google.common.primitives.Longs;
import com.google.inject.servlet.RequestScoped;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.inject.Inject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.core.StreamingOutput;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.csv.CSVPrinter;
import org.eclipse.persistence.annotations.Transformation;

@RequestScoped
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
@Path("/dms")
@Tag(name = "DMS")
public class DmsService {

  @Context private HttpServletRequest httpRequest;

  @Inject private DMSFileRepository repository;

  private static final Map<String, String> EXTS =
      ImmutableMap.of("html", ".html", "spreadsheet", ".csv");

  @GET
  @Path("files")
  @Operation(
      summary = "File listing",
      description = "This service can be used to find list of files under a specific directory.")
  public Response listFiles(
      @QueryParam("parent") Long parentId, @QueryParam("pattern") String pattern) {
    final Response response = new Response();
    final StringBuilder filter = new StringBuilder("self.parent");

    if (parentId == null || parentId <= 0) {
      filter.append(" is null");
    } else {
      filter.append(" = :parent");
    }
    if (!StringUtils.isBlank(pattern)) {
      pattern = "%" + pattern + "%";
      filter.append(" AND UPPER(self.fileName) like UPPER(:pattern)");
    }

    final Query<?> query =
        repository
            .all()
            .filter(filter.toString())
            .bind("parent", parentId)
            .bind("pattern", pattern);

    final Long count = query.count();
    final List<?> records = query.select("fileName", "isDirectory").fetch(-1, -1);

    response.setStatus(Response.STATUS_SUCCESS);
    response.setData(records);
    response.setTotal(count);
    return response;
  }

  @GET
  @Path("attachments/{model}/{id}")
  @Operation(
      summary = "List attachments",
      description =
          "This service can be used to find list of files attached to some specific record.")
  public Response attachments(@PathParam("model") String model, @PathParam("id") Long id) {
    final Response response = new Response();
    final List<?> records =
        repository
            .all()
            .filter(
                "self.relatedId = :id AND self.relatedModel = :model AND self.metaFile is not null AND self.isDirectory = false")
            .bind("id", id)
            .bind("model", model)
            .select("fileName")
            .fetch(-1, -1);
    response.setStatus(Response.STATUS_SUCCESS);
    response.setData(records);
    response.setTotal(records.size());
    return response;
  }

  @PUT
  @Path("attachments/{model}/{id}")
  @Operation(
      summary = "Add attachment",
      description =
          "The MetaFile record obtained with upload service can be used to create attachments.")
  @Transformation
  public Response addAttachments(
      @PathParam("model") String model, @PathParam("id") Long id, Request request) {
    if (request == null || ObjectUtils.isEmpty(request.getRecords())) {
      throw new IllegalArgumentException("No attachment records provided.");
    }
    final Class<?> modelClass;
    try {
      modelClass = Class.forName(model);
    } catch (ClassNotFoundException e) {
      throw new IllegalArgumentException("No such model found.");
    }
    final Object entity = JPA.em().find(modelClass, id);
    if (!(entity instanceof Model)) {
      throw new IllegalArgumentException("No such record found.");
    }

    final MetaFileRepository filesRepo = Beans.get(MetaFileRepository.class);
    final List<MetaFile> items = new ArrayList<>();

    for (Object item : request.getRecords()) {
      @SuppressWarnings("rawtypes")
      Object fileRecord = filesRepo.find(Longs.tryParse(((Map) item).get("id").toString()));
      if (fileRecord instanceof MetaFile) {
        items.add((MetaFile) fileRecord);
      } else {
        throw new IllegalArgumentException("Invalid list of attachment records.");
      }
    }

    final MetaFiles files = Beans.get(MetaFiles.class);
    final Response response = new Response();
    final List<Object> records = new ArrayList<>();

    for (MetaFile file : items) {
      DMSFile dmsFile = files.attach(file, file.getFileName(), (Model) entity);
      records.add(Resource.toMapCompact(dmsFile));
    }

    response.setStatus(Response.STATUS_SUCCESS);
    response.setData(records);
    return response;
  }

  @GET
  @Path("offline")
  @Hidden
  public Response getOfflineFiles(
      @QueryParam("limit") int limit, @QueryParam("offset") int offset) {

    final Response response = new Response();
    final List<DMSFile> files = repository.findOffline(limit, offset);
    final long count =
        repository
            .all()
            .filter("self.permissions[].value = 'OFFLINE' AND self.permissions[].user = :user")
            .bind("user", AuthUtils.getUser())
            .count();

    final List<Object> data = new ArrayList<>();
    for (DMSFile file : files) {
      final Map<String, Object> json = Resource.toMap(file, "fileName");
      final MetaFile metaFile = file.getMetaFile();
      LocalDateTime lastModified = file.getUpdatedOn();
      if (metaFile != null) {
        lastModified = metaFile.getCreatedOn();
        json.put("fileSize", metaFile.getFileSize());
        json.put("fileType", metaFile.getFileType());
      }

      json.put("id", file.getId());
      json.put("updatedOn", lastModified);

      data.add(json);
    }

    response.setData(data);
    response.setOffset(offset);
    response.setTotal(count);
    response.setStatus(Response.STATUS_SUCCESS);

    return response;
  }

  @POST
  @Path("offline")
  @Hidden
  public Response offline(Request request) {
    final Response response = new Response();
    final List<?> ids = request.getRecords();

    if (ids == null || ids.isEmpty()) {
      response.setStatus(Response.STATUS_SUCCESS);
      return response;
    }

    final List<DMSFile> records =
        repository.all().filter("self.id in :ids").bind("ids", ids).fetch();

    boolean unset;
    try {
      unset = "true".equals(request.getData().get("unset").toString());
    } catch (Exception e) {
      unset = false;
    }

    for (DMSFile item : records) {
      repository.setOffline(item, unset);
    }

    response.setStatus(Response.STATUS_SUCCESS);
    return response;
  }

  /**
   * Check if the associated metaFile or content of the given DMSFile exists
   *
   * @param file the dmsFile to check
   * @return true if the file exists, otherwise false
   */
  private boolean hasFile(DMSFile file) {
    if (file == null) {
      return false;
    }

    if (file.getMetaFile() != null) {
      return FileStoreFactory.getStore().hasFile(file.getMetaFile().getFilePath());
    }

    return file.getContent() != null;
  }

  @HEAD
  @Path("offline/{id}")
  @Hidden
  public jakarta.ws.rs.core.Response doDownloadCheck(@PathParam("id") long id) {
    final DMSFile file = repository.find(id);
    return !hasFile(file)
        ? jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build()
        : jakarta.ws.rs.core.Response.ok().build();
  }

  @GET
  @Path("offline/{id}")
  @Hidden
  public jakarta.ws.rs.core.Response doDownload(@PathParam("id") long id) {

    final DMSFile file = repository.find(id);
    if (!hasFile(file)) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }

    final StreamingOutput so =
        new StreamingOutput() {
          @Override
          public void write(OutputStream output) throws IOException, WebApplicationException {
            try (InputStream input =
                FileStoreFactory.getStore().getStream(file.getMetaFile().getFilePath())) {
              writeTo(output, input);
            }
          }
        };

    return stream(so, file.getFileName(), false);
  }

  @POST
  @Path("download/batch")
  @Hidden
  public jakarta.ws.rs.core.Response onDownload(Request request) {

    final List<Object> ids = request.getRecords();

    if (ids == null || ids.isEmpty()) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }

    final List<DMSFile> records =
        repository.all().filter("self.id in :ids").bind("ids", ids).fetch();

    if (records.size() != ids.size()) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }

    // Check if all files exist
    if (records.stream()
        .anyMatch(dmsFile -> !Boolean.TRUE.equals(dmsFile.getIsDirectory()) && !hasFile(dmsFile))) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }

    final String batchId = UUID.randomUUID().toString();
    final Map<String, Object> data = new HashMap<>();

    String batchName = "documents-" + LocalDate.now() + ".zip";
    if (records.size() == 1) {
      batchName = records.getFirst().getFileName();
    }

    data.put("batchId", batchId);
    data.put("batchName", batchName);

    final HttpSession session = httpRequest.getSession(false);
    if (session != null) {
      session.setAttribute(batchId, ids);
    }

    return jakarta.ws.rs.core.Response.ok(data).build();
  }

  private boolean hasBatchIds(String batchIds) {
    final HttpSession session = httpRequest.getSession(false);
    return session != null && ObjectUtils.notEmpty((List<?>) session.getAttribute(batchIds));
  }

  private List<?> findBatchIds(String batchOrId) {
    final HttpSession session = httpRequest.getSession(false);
    List<?> ids = session != null ? (List<?>) session.getAttribute(batchOrId) : null;
    if (ids == null) {
      Long id = Longs.tryParse(batchOrId);
      ids = id == null ? null : Arrays.asList(id);
    }

    if (ids == null || ids.isEmpty()) {
      return null;
    }
    return ids;
  }

  @HEAD
  @Path("download/{id}")
  @Operation(
      summary = "Check file existence",
      description = "Check that the specified DMS file exists.")
  public jakarta.ws.rs.core.Response doDownloadCheck(@PathParam("id") String batchOrId) {
    if (!hasBatchIds(batchOrId) && !hasFile(repository.find(Longs.tryParse(batchOrId)))) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }
    return findBatchIds(batchOrId) == null
        ? jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build()
        : jakarta.ws.rs.core.Response.ok().build();
  }

  @GET
  @Path("download/{id}")
  @Produces(MediaType.APPLICATION_OCTET_STREAM)
  @Operation(
      summary = "File download",
      description =
          "This service can be used to download a file. It should be used as normal http request.")
  public jakarta.ws.rs.core.Response doDownload(@PathParam("id") String batchOrId) {
    return getAttachmentResponse(batchOrId, false);
  }

  @GET
  @Path("inline/{id}")
  @Hidden
  public jakarta.ws.rs.core.Response doInline(@PathParam("id") String batchOrId) {
    return getAttachmentResponse(batchOrId, true);
  }

  private jakarta.ws.rs.core.Response getAttachmentResponse(String batchOrId, boolean inline) {
    final List<?> ids = findBatchIds(batchOrId);
    if (ids == null) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }

    final Long[] idArray =
        ids.stream()
            .map(
                id ->
                    id instanceof Number ? ((Number) id).longValue() : Long.valueOf(id.toString()))
            .toArray(Long[]::new);

    if (!Beans.get(JpaSecurity.class).isPermitted(JpaSecurity.CAN_READ, DMSFile.class, idArray)) {
      return jakarta.ws.rs.core.Response.status(Status.FORBIDDEN).build();
    }

    final List<DMSFile> records =
        repository.all().filter("self.id in :ids").bind("ids", ids).fetch();

    if (records.size() != ids.size()) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }

    // if file
    final DMSFile record = records.getFirst();
    if (records.size() == 1 && !record.getIsDirectory()) {
      File file = getFile(record);
      if (file != null && hasFile(record)) {
        return stream(file, getFileName(record), inline);
      } else {
        return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
      }
    }

    final StreamingOutput so =
        new StreamingOutput() {
          @Override
          public void write(OutputStream output) throws IOException, WebApplicationException {
            try (final ZipOutputStream zos = new ZipOutputStream(output)) {
              for (DMSFile file : records) {
                writeToZip(zos, file);
              }
            }
          }
        };

    final String batchName = "documents-" + LocalDate.now() + ".zip";
    try {
      return stream(so, batchName, inline);
    } catch (Exception e) {
      return jakarta.ws.rs.core.Response.status(Status.NOT_FOUND).build();
    }
  }

  private File getFile(DMSFile record) {
    if (record.getMetaFile() != null) {
      MetaFile file = record.getMetaFile();
      return MetaFiles.getPath(file).toFile();
    }

    if (StringUtils.isBlank(record.getContentType())) {
      return null;
    }

    try {
      switch (record.getContentType()) {
        case "html":
          {
            final java.nio.file.Path path = TempFiles.createTempFile(record.getFileName(), ".html");
            final File file = path.toFile();
            if (StringUtils.notBlank(record.getContent())) {
              try (final FileWriter writer = new FileWriter(file)) {
                writer.append(record.getContent());
              }
            }
            return file;
          }
        case "spreadsheet":
          {
            final java.nio.file.Path path = TempFiles.createTempFile(record.getFileName(), ".csv");
            final File file = path.toFile();

            if (StringUtils.isBlank(record.getContent())) {
              return file;
            }

            final ScriptHelper scriptHelper = new GroovyScriptHelper(null);
            final List<?> content = (List<?>) scriptHelper.eval(record.getContent());

            if (content == null || content.isEmpty()) {
              return file;
            }

            final List<String[]> lines =
                content.stream()
                    .map(line -> (List<?>) line)
                    .map(line -> line.toArray(new String[] {}))
                    .collect(Collectors.toList());

            try (CSVPrinter printer = CSVFile.DEFAULT.write(file)) {
              printer.printRecords(lines);
            }

            return file;
          }
        default:
          throw new IllegalArgumentException("Unsupported content type");
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private InputStream getStream(DMSFile dmsFile) {
    if (dmsFile.getMetaFile() != null) {
      return FileStoreFactory.getStore().getStream(dmsFile.getMetaFile().getFilePath());
    }
    try {
      return new FileInputStream(getFile(dmsFile));
    } catch (FileNotFoundException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String getFileName(DMSFile record) {
    return record.getFileName() + EXTS.getOrDefault(record.getContentType(), "");
  }

  private Map<String, DMSFile> findFiles(DMSFile file, String base) {
    final User user = AuthUtils.getUser();

    if (user == null) {
      return Collections.emptyMap();
    }

    String childrenQlString = "self.parent = :parent";

    if (!AuthUtils.isAdmin(user)) {
      childrenQlString += " AND (self.permissions.user = :user OR self.permissions.group = :group)";
    }

    return findFiles(file, base, childrenQlString, user);
  }

  private Map<String, DMSFile> findFiles(
      DMSFile dmsFile, String base, String childrenQlString, User user) {
    final Map<String, DMSFile> files = new LinkedHashMap<>();
    if (Boolean.TRUE.equals(dmsFile.getIsDirectory())) {
      final List<DMSFile> children =
          repository
              .all()
              .filter(childrenQlString, dmsFile, user, user.getGroup())
              .bind("parent", dmsFile)
              .bind("user", user)
              .bind("group", user.getGroup())
              .fetch();
      final String path = base + "/" + dmsFile.getFileName();
      files.put(path + "/", null);
      for (DMSFile child : children) {
        files.putAll(findFiles(child, path, childrenQlString, user));
      }
      return files;
    }
    if (isDownloadable(dmsFile)) {
      files.put(base + "/" + getFileName(dmsFile), dmsFile);
    }
    return files;
  }

  private void writeToZip(ZipOutputStream zos, DMSFile dmsFile) throws IOException {
    final Map<String, DMSFile> files = findFiles(dmsFile, "");
    for (final String entry : files.keySet()) {
      DMSFile file = files.get(entry);
      zos.putNextEntry(new ZipEntry(entry.charAt(0) == '/' ? entry.substring(1) : entry));
      if (file == null) {
        zos.closeEntry();
        continue;
      }
      final InputStream fis = getStream(file);
      try {
        writeTo(zos, fis);
      } finally {
        fis.close();
        zos.closeEntry();
      }
    }
  }

  private boolean isDownloadable(DMSFile dmsFile) {
    if (hasFile(dmsFile)) {
      return true;
    }
    if (StringUtils.isBlank(dmsFile.getContentType())) {
      return false;
    }
    return "html".equals(dmsFile.getContentType())
        || "spreadsheet".equals(dmsFile.getContentType());
  }

  private void writeTo(OutputStream os, InputStream is) throws IOException {
    int read = 0;
    byte[] bytes = new byte[2048];
    while ((read = is.read(bytes)) != -1) {
      os.write(bytes, 0, read);
    }
  }

  private jakarta.ws.rs.core.Response stream(Object content, String fileName, boolean inline) {
    MediaType type = MediaType.APPLICATION_OCTET_STREAM_TYPE;

    if (inline) {
      if (fileName.endsWith(".pdf")) type = new MediaType("application", "pdf");
      if (fileName.endsWith(".html")) type = new MediaType("text", "html");
      if (fileName.endsWith(".png")) type = new MediaType("image", "png");
      if (fileName.endsWith(".jpg")) type = new MediaType("image", "jpg");
      if (fileName.endsWith(".jpeg")) type = new MediaType("image", "jpg");
      if (fileName.endsWith(".svg")) type = new MediaType("image", "svg+xml");
      if (fileName.endsWith(".gif")) type = new MediaType("image", "gif");
      if (fileName.endsWith(".webp")) type = new MediaType("image", "webp");
    }

    final ResponseBuilder builder = jakarta.ws.rs.core.Response.ok(content, type);

    if (inline && type != MediaType.APPLICATION_OCTET_STREAM_TYPE) {
      return builder
          .header(
              "Content-Disposition",
              ContentDisposition.inline().filename(fileName).build().toString())
          .build();
    }

    return builder
        .header(
            "Content-Disposition",
            ContentDisposition.attachment().filename(fileName).build().toString())
        .header("Content-Transfer-Encoding", "binary")
        .build();
  }
}
