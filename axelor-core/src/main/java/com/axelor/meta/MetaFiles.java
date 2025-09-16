/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.meta;

import static com.axelor.common.StringUtils.isBlank;
import static com.axelor.common.StringUtils.notBlank;

import com.axelor.app.AppSettings;
import com.axelor.app.AvailableAppSettings;
import com.axelor.common.FileUtils;
import com.axelor.common.MimeTypesUtils;
import com.axelor.common.StringUtils;
import com.axelor.db.EntityHelper;
import com.axelor.db.Model;
import com.axelor.dms.db.DMSFile;
import com.axelor.dms.db.repo.DMSFileRepository;
import com.axelor.file.store.FileStoreFactory;
import com.axelor.file.store.Store;
import com.axelor.file.store.StoreType;
import com.axelor.file.store.UploadedFile;
import com.axelor.file.temp.TempFiles;
import com.axelor.inject.Beans;
import com.axelor.meta.db.MetaAttachment;
import com.axelor.meta.db.MetaFile;
import com.axelor.meta.db.repo.MetaAttachmentRepository;
import com.axelor.meta.db.repo.MetaFileRepository;
import com.google.inject.persist.Transactional;
import jakarta.activation.MimeType;
import jakarta.activation.MimeTypeParseException;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.core.MediaType;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** This class provides some helper methods to deal with files. */
public class MetaFiles {

  private static final String UPLOAD_NAME_PATTERN_AUTO = "auto";

  private static final Object lock = new Object();

  private static final List<Pattern> WHITELIST_PATTERNS =
      AppSettings.get()
          .getList(AvailableAppSettings.FILE_UPLOAD_WHITELIST_PATTERN, Pattern::compile);
  private static final List<Pattern> BLACKLIST_PATTERNS =
      AppSettings.get()
          .getList(AvailableAppSettings.FILE_UPLOAD_BLACKLIST_PATTERN, Pattern::compile);
  private static final List<MimeType> WHITELIST_TYPES =
      getMimeTypes(AvailableAppSettings.FILE_UPLOAD_WHITELIST_TYPES);
  private static final List<MimeType> BLACKLIST_TYPES =
      getMimeTypes(AvailableAppSettings.FILE_UPLOAD_BLACKLIST_TYPES);

  private MetaFileRepository filesRepo;

  @Inject
  public MetaFiles(MetaFileRepository filesRepo) {
    this.filesRepo = filesRepo;
  }

  private static List<MimeType> getMimeTypes(String key) {
    return AppSettings.get()
        .getList(
            key,
            (s) -> {
              try {
                return new MimeType(s);
              } catch (MimeTypeParseException e) {
                throw new RuntimeException("Invalid file type: " + s + " for '" + key + "'");
              }
            });
  }

  /**
   * Get the storage path of the file represented by the give {@link MetaFile} instance.
   *
   * @param file the given {@link MetaFile} instance
   * @return actual file path
   */
  public static Path getPath(MetaFile file) {
    Objects.requireNonNull(file, "file instance can't be null");
    Store store = FileStoreFactory.getStore();
    return store.getPath(file.getFilePath());
  }

  /**
   * Get the storage path of the given relative file path.
   *
   * @param filePath relative file path
   * @return actual file path
   */
  public static Path getPath(String filePath) {
    Objects.requireNonNull(filePath, "file path can't be null");
    Store store = FileStoreFactory.getStore();
    return store.getPath(filePath);
  }

  /**
   * Check whether the given filePath is valid.
   *
   * <p>The filePath is valid if it matches upload file whitelist pattern and doesn't match upload
   * blacklist pattern.
   *
   * @param filePath the file path to check
   * @throws IllegalArgumentException if the file path to check is not valid
   */
  public static void checkPath(String filePath) {
    Objects.requireNonNull(filePath, "file path can't be null");

    boolean blocked =
        !BLACKLIST_PATTERNS.isEmpty() && isMatchingFileNamePattern(BLACKLIST_PATTERNS, filePath);

    if (blocked) {
      throw new IllegalArgumentException("File name is not allowed: " + filePath);
    }

    boolean allowed =
        WHITELIST_PATTERNS.isEmpty() || isMatchingFileNamePattern(WHITELIST_PATTERNS, filePath);

    if (!allowed) {
      throw new IllegalArgumentException("File name is not allowed: " + filePath);
    }
  }

  private static boolean isMatchingFileNamePattern(List<Pattern> patterns, String filePath) {
    return patterns.stream().map(p -> p.matcher(filePath)).anyMatch(Matcher::find);
  }

  /**
   * Check whether the given file type is valid.
   *
   * <p>The file is valid if it matches file upload whitelist types and doesn't match upload
   * blacklist types.
   *
   * @param fileType the file type to check
   * @throws IllegalArgumentException if the file type to check is not valid
   */
  public static void checkType(String fileType) {
    if (StringUtils.isBlank(fileType)) {
      return;
    }

    final MimeType mimeType;
    try {
      mimeType = new MimeType(fileType);
    } catch (MimeTypeParseException e) {
      return;
    }

    boolean blocked = !BLACKLIST_TYPES.isEmpty() && isMatchingMimeType(BLACKLIST_TYPES, mimeType);

    if (blocked) {
      throw new IllegalArgumentException("File type is not allowed: " + fileType);
    }

    boolean allowed = WHITELIST_TYPES.isEmpty() || isMatchingMimeType(WHITELIST_TYPES, mimeType);

    if (!allowed) {
      throw new IllegalArgumentException("File type is not allowed: " + fileType);
    }
  }

  private static boolean isMatchingMimeType(List<MimeType> mimeTypes, MimeType actualMimeType) {
    return mimeTypes.stream().anyMatch(m -> m.match(actualMimeType));
  }

  /**
   * Check whether the given file is valid.
   *
   * <p>The file is valid if it matches file upload whitelist types and doesn't match upload
   * blacklist types.
   *
   * @param file the file to check
   * @throws IllegalArgumentException
   */
  public static void checkType(File file) {
    Objects.requireNonNull(file, "file can't be null");
    checkType(MimeTypesUtils.getContentType(file));
  }

  private String getTargetName(String fileName) {
    String targetName =
        AppSettings.get()
            .get(AvailableAppSettings.FILE_UPLOAD_FILENAME_PATTERN, UPLOAD_NAME_PATTERN_AUTO);

    if (targetName.equals(UPLOAD_NAME_PATTERN_AUTO)) {
      return fileName;
    }

    if (targetName.contains("{A}")) {
      targetName = targetName.replace("{A}", fileName.substring(0, 1).toUpperCase());
    }
    if (targetName.contains("{AA}")) {
      targetName =
          targetName.replace(
              "{AA}", fileName.substring(0, Math.min(2, fileName.length())).toUpperCase());
    }
    if (targetName.contains("{AAA}")) {
      targetName =
          targetName.replace(
              "{AAA}", fileName.substring(0, Math.min(3, fileName.length())).toUpperCase());
    }

    if (targetName.contains("{name}")) {
      targetName = targetName.replace("{name}", fileName);
    } else {
      targetName = Path.of(targetName, fileName).toString();
    }

    return targetName;
  }

  private String resolveFileName(String fileName, Store store) {
    synchronized (lock) {
      int dotIndex = fileName.lastIndexOf('.');
      int counter = 1;
      String fileNameBase = fileName;
      String fileNameExt = "";
      if (dotIndex > -1) {
        fileNameExt = fileName.substring(dotIndex);
        fileNameBase = fileName.substring(0, dotIndex);
      }
      String targetName = getTargetName(fileName);
      while (store.hasFile(targetName)) {
        targetName = fileNameBase + "-" + counter++ + fileNameExt;
      }
      return targetName;
    }
  }

  /**
   * Upload the given chunk of file data to a temporary file identified by the given file id.
   *
   * <p>Upload would restart if startOffset is 0 (zero), otherwise upload file size is checked
   * against given startOffset. The startOffset must be less than expected fileSize.
   *
   * <p>Unlike the {@link #upload(File, MetaFile)} or {@link #upload(File)} methods, this method
   * doesn't create {@link MetaFile} instance.
   *
   * <p>The temporary file generated should be manually uploaded again using {@link #upload(File,
   * MetaFile)} or should be deleted using {@link TempFiles#clean(String)} method if something went
   * wrong.
   *
   * @param chunk the input stream
   * @param startOffset the start offset byte position
   * @param fileSize the actual file size
   * @param fileId an unique upload file identifier
   * @return a temporary file where upload is being saved
   * @throws IOException if there is any error during io operations
   */
  public File upload(InputStream chunk, long startOffset, long fileSize, String fileId)
      throws IOException {
    final Path tmp = TempFiles.findTempFile(fileId);
    if ((fileSize > -1 && startOffset > fileSize)
        || (Files.exists(tmp) && Files.size(tmp) != startOffset)
        || (!Files.exists(tmp) && startOffset > 0)) {
      throw new IllegalArgumentException("Start offset is out of bound.");
    }

    // clean up obsolete temporary files
    try {
      TempFiles.clean();
    } catch (Exception e) {
      // ignore
    }

    FileUtils.write(tmp, chunk, startOffset > 0);

    return tmp.toFile();
  }

  /**
   * Upload the given file to the file upload directory and create an instance of {@link MetaFile}
   * for the given file.
   *
   * @param file the given file
   * @return an instance of {@link MetaFile}
   * @throws IOException if unable to read the file
   * @throws PersistenceException if unable to save to a {@link MetaFile} instance
   */
  @Transactional
  public MetaFile upload(File file) throws IOException {
    return upload(file, new MetaFile());
  }

  /**
   * Upload the given {@link File} to the upload directory and link it to the to given {@link
   * MetaFile}.
   *
   * <p>Any existing file linked to the given {@link MetaFile} will be removed from the upload
   * directory.
   *
   * @param file the file to upload
   * @param metaFile the target {@link MetaFile} instance
   * @return persisted {@link MetaFile} instance
   * @throws IOException if unable to read the file
   * @throws PersistenceException if unable to save to {@link MetaFile} instance
   */
  @Transactional
  public MetaFile upload(File file, MetaFile metaFile) throws IOException {
    Objects.requireNonNull(metaFile);
    Objects.requireNonNull(file);

    final Store store = FileStoreFactory.getStore();
    final String originalFilePath = metaFile.getFilePath();
    final boolean isExist = notBlank(originalFilePath) && store.hasFile(originalFilePath);

    // Create a tmp copy of the file in case of recovery
    File tmpCopy = null;
    try {
      if (isExist) {
        if (store.getStoreType() == StoreType.FILE_SYSTEM) {
          tmpCopy = TempFiles.createTempFile().toFile();
          FileUtils.copyFile(store.getFile(originalFilePath), tmpCopy);
          store.deleteFile(originalFilePath);
        } else {
          tmpCopy = store.getFile(originalFilePath);
          store.deleteFile(originalFilePath);
        }
      }

      String fileName = isBlank(metaFile.getFileName()) ? file.getName() : metaFile.getFileName();
      String filePath;

      filePath = resolveFileName(fileName, store);
      UploadedFile uploadedFile = store.addFile(file, filePath);

      metaFile.setFileName(fileName);
      metaFile.setFileType(uploadedFile.getContentType());
      metaFile.setFileSize(uploadedFile.getSize());
      metaFile.setFilePath(uploadedFile.getPath());
      metaFile.setStoreType(uploadedFile.getStoreType().getValue());

      try {
        return filesRepo.save(metaFile);
      } catch (Exception e) {
        // delete the uploaded file
        try {
          store.deleteFile(filePath);
        } catch (Exception ex) {
          // ignore, file may not completely uploaded
        }
        // restore original file
        store.addFile(tmpCopy, originalFilePath);
        throw new PersistenceException(e);
      }
    } finally {
      if (tmpCopy != null) {
        Files.deleteIfExists(tmpCopy.toPath());
      }
    }
  }

  /**
   * Upload the given stream to the upload directory and link it to the given {@link MetaFile}.
   *
   * <p>The given {@link MetaFile} instance must have fileName set to save the stream as file.
   * Upload the stream
   *
   * @param stream the stream to upload
   * @param metaFile the {@link MetaFile} to link the uploaded file
   * @return the given {@link MetaFile} instance
   * @throws IOException if an I/O error occurs
   */
  @Transactional
  public MetaFile upload(InputStream stream, MetaFile metaFile) throws IOException {
    Objects.requireNonNull(stream, "stream can't be null");
    Objects.requireNonNull(metaFile, "meta file can't be null");
    Objects.requireNonNull(metaFile.getFileName(), "meta file should have filename");

    final Path tmp = TempFiles.createTempFile();
    final File tmpFile = upload(stream, 0, -1, tmp.toFile().getName());

    return upload(tmpFile, metaFile);
  }

  /**
   * Upload the given stream to the upload directory.
   *
   * @param stream the stream to upload
   * @param fileName the file name to use
   * @return a new {@link MetaFile} instance
   * @throws IOException if an I/O error occurs
   */
  @Transactional
  public MetaFile upload(InputStream stream, String fileName) throws IOException {
    final MetaFile file = new MetaFile();
    file.setFileName(fileName);
    return upload(stream, file);
  }

  /**
   * Upload the given file stream and attach it to the given record.
   *
   * <p>The attachment will be saved as {@link DMSFile} and will be visible in DMS user interface.
   * Use {@link #upload(InputStream, String)} along with {@link #attach(MetaFile, Model)} if you
   * don't want to show the attachment in DMS interface.
   *
   * @param stream the stream to upload
   * @param fileName the file name to use
   * @param entity the record to attach to
   * @return a {@link DMSFile} record created for the attachment
   * @throws IOException if an I/O error occurs
   */
  @Transactional
  public DMSFile attach(InputStream stream, String fileName, Model entity) throws IOException {
    final MetaFile metaFile = upload(stream, fileName);
    return attach(metaFile, fileName, entity);
  }

  /**
   * Attach the given file to the given record.
   *
   * @param metaFile the file to attach
   * @param fileName alternative file name to use (optional, can be null)
   * @param entity the record to attach to
   * @return a {@link DMSFile} record created for the attachment
   */
  @Transactional
  public DMSFile attach(MetaFile metaFile, String fileName, Model entity) {
    Objects.requireNonNull(metaFile);
    Objects.requireNonNull(metaFile.getId());
    Objects.requireNonNull(entity);
    Objects.requireNonNull(entity.getId());

    final String name = isBlank(fileName) ? metaFile.getFileName() : fileName;
    final DMSFile dmsFile = new DMSFile();
    final DMSFileRepository repository = Beans.get(DMSFileRepository.class);

    dmsFile.setFileName(name);
    dmsFile.setMetaFile(metaFile);
    dmsFile.setRelatedId(entity.getId());
    dmsFile.setRelatedModel(EntityHelper.getEntityClass(entity).getName());

    repository.save(dmsFile);

    return dmsFile;
  }

  /**
   * Delete the given {@link DMSFile} and also delete linked file if not referenced by any other
   * record.
   *
   * <p>It will attempt to clean up associated {@link MetaFile} and {@link MetaAttachment} records
   * and also try to delete linked file from upload directory.
   *
   * @param file the {@link DMSFile} to delete
   */
  @Transactional
  public void delete(DMSFile file) {
    final DMSFileRepository repository = Beans.get(DMSFileRepository.class);
    repository.remove(file);
  }

  /**
   * Attach the given {@link MetaFile} to the given {@link Model} object and return an instance of a
   * {@link MetaAttachment} that represents the attachment.
   *
   * <p>The {@link MetaAttachment} instance is not persisted.
   *
   * @param file the given {@link MetaFile} instance
   * @param entity the given {@link Model} instance
   * @return a new instance of {@link MetaAttachment}
   */
  public MetaAttachment attach(MetaFile file, Model entity) {
    Objects.requireNonNull(file);
    Objects.requireNonNull(entity);
    Objects.requireNonNull(entity.getId());

    MetaAttachment attachment = new MetaAttachment();
    attachment.setMetaFile(file);
    attachment.setObjectId(entity.getId());
    attachment.setObjectName(EntityHelper.getEntityClass(entity).getName());

    return attachment;
  }

  /**
   * Delete the given attachment and related {@link MetaFile} instance along with the file content.
   *
   * @param attachment the attachment to delete
   * @throws IOException if unable to delete file
   */
  @Transactional
  public void delete(MetaAttachment attachment) throws IOException {
    Objects.requireNonNull(attachment);
    Objects.requireNonNull(attachment.getMetaFile());

    MetaAttachmentRepository attachments = Beans.get(MetaAttachmentRepository.class);
    DMSFileRepository dms = Beans.get(DMSFileRepository.class);

    attachments.remove(attachment);

    MetaFile metaFile = attachment.getMetaFile();
    long count = dms.all().filter("self.metaFile = ?", metaFile).count();
    if (count == 0) {
      count =
          attachments
              .all()
              .filter("self.metaFile = ? and self.id != ?", metaFile, attachment.getId())
              .count();
    }

    // only delete real file if not referenced anywhere else
    if (count > 0) {
      return;
    }

    this.delete(metaFile);
  }

  /**
   * Delete the given {@link MetaFile} instance along with the file content if it exists.
   *
   * @param metaFile the file to delete
   * @throws IOException if unable to delete file
   */
  @Transactional
  public void delete(MetaFile metaFile) throws IOException {
    Objects.requireNonNull(metaFile);

    filesRepo.remove(metaFile);

    Store store = FileStoreFactory.getStore();
    try {
      if (store.hasFile(metaFile.getFilePath())) {
        store.deleteFile(metaFile.getFilePath());
      }
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * Deletes all attachments and related records (MetaFile, DMSFile, MetaAttachment). Finally,
   * deletes all real files.
   *
   * @param entity
   */
  @Transactional
  public void deleteAttachments(Model entity) {
    final DMSFileRepository dmsFileRepo = Beans.get(DMSFileRepository.class);
    Optional.ofNullable(dmsFileRepo.findHomeByRelated(entity)).ifPresent(dmsFileRepo::remove);
  }

  public String fileTypeIcon(MetaFile file) {
    String fileType = file.getFileType();
    if (fileType == null) {
      return "file-earmark";
    }
    switch (fileType) {
      case "application/msword":
      case "application/vnd.openxmlformats-officedocument.wordprocessingml.document":
      case "application/vnd.oasis.opendocument.text":
        return "file-earmark-word";
      case "application/vnd.ms-excel":
      case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet":
      case "application/vnd.oasis.opendocument.spreadsheet":
        return "file-earmark-excel";
      case "application/vnd.ms-powerpoint":
      case "application/vnd.openxmlformats-officedocument.presentationml.presentation":
      case "application/vnd.oasis.opendocument.presentation":
        return "file-earmark-ppt";
      case "application/pdf":
        return "file-earmark-pdf";
      case "application/zip":
      case "application/gzip":
        return "file-earmark-zip";
      default:
        if (fileType.startsWith("text")) return "file-earmark-text";
        if (fileType.startsWith("image")) return "file-earmark-image";
        if (fileType.startsWith("video")) return "file-earmark-play";
    }
    return "file-earmark";
  }

  /**
   * Gets download link for given meta file. Permissions can be checked from given parent record.
   *
   * @param metaFile
   * @param parentModel
   * @return download link
   */
  public String getDownloadLink(MetaFile metaFile, Model parentModel) {
    return "ws/rest/%s/%d/content/download?parentModel=%s&parentId=%d"
        .formatted(
            MetaFile.class.getName(),
            metaFile.getId(),
            EntityHelper.getEntityClass(parentModel).getName(),
            parentModel.getId());
  }

  private static final MediaType[] BROWSER_PREVIEW_COMPATIBLE_MEDIA_TYPE = {
    new MediaType("application", "pdf"), new MediaType("image", "*"), new MediaType("text", "html")
  };

  public static boolean isBrowserPreviewCompatible(MediaType mediaType) {
    return mediaType != null
        && Arrays.stream(BROWSER_PREVIEW_COMPATIBLE_MEDIA_TYPE)
            .anyMatch(s -> s.isCompatible(mediaType));
  }
}
