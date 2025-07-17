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
package com.axelor.common;

import static java.nio.file.FileVisitOption.FOLLOW_LINKS;
import static java.nio.file.FileVisitResult.CONTINUE;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.EnumSet;
import java.util.Objects;

/** This class provides some helper methods to deal with files. */
public final class FileUtils {

  /**
   * Get a file from the given path elements.
   *
   * @param first the first path element
   * @param more the additional path elements
   * @return the file
   */
  public static File getFile(String first, String... more) {
    Objects.requireNonNull(first, "first element must not be null");
    File file = new File(first);
    if (more != null) {
      for (String name : more) {
        file = new File(file, name);
      }
    }
    return file;
  }

  /**
   * Get a file from the given path elements.
   *
   * @param directory the parent directory
   * @param next next path element
   * @param more additional path elements
   * @return the file
   */
  public static File getFile(File directory, String next, String... more) {
    Objects.requireNonNull(directory, "directory must not be null");
    Objects.requireNonNull(next, "next element must not be null");
    File file = new File(directory, next);
    if (more != null) {
      for (String name : more) {
        file = new File(file, name);
      }
    }
    return file;
  }

  /**
   * Copy the source directory to the target directory.
   *
   * @param source the source directory
   * @param target the target directory
   * @throws IOException if IO error occurs during copying
   */
  public static void copyDirectory(File source, File target) throws IOException {
    copyDirectory(source.toPath(), target.toPath());
  }

  /**
   * Copy the source directory to the target directory.
   *
   * @param source the source directory
   * @param target the target directory
   * @throws IOException if IO error occurs during copying
   */
  public static void copyDirectory(Path source, Path target) throws IOException {
    if (!Files.isDirectory(source)) {
      throw new IOException("Invalid source directory: " + source);
    }
    if (Files.exists(target) && !Files.isDirectory(target)) {
      throw new IOException("Invalid target directory: " + target);
    }
    if (!Files.exists(target)) {
      Files.createDirectories(target);
    }
    final DirCopier copier = new DirCopier(source, target);
    final EnumSet<FileVisitOption> opts = EnumSet.of(FOLLOW_LINKS);
    Files.walkFileTree(source, opts, Integer.MAX_VALUE, copier);
  }

  /**
   * Delete the given directory recursively.
   *
   * @param directory the directory to delete
   * @throws IOException in case deletion is unsuccessful
   */
  public static void deleteDirectory(File directory) throws IOException {
    deleteDirectory(directory.toPath());
  }

  /**
   * Delete the given directory recursively.
   *
   * @param directory the directory to delete
   * @throws IOException in case deletion is unsuccessful
   */
  public static void deleteDirectory(Path directory) throws IOException {
    if (!Files.isDirectory(directory)) {
      throw new IOException("Invalid directory: " + directory);
    }
    final DirCleaner cleaner = new DirCleaner();
    final EnumSet<FileVisitOption> opts = EnumSet.of(FOLLOW_LINKS);
    Files.walkFileTree(directory, opts, Integer.MAX_VALUE, cleaner);
  }

  static class DirCopier extends SimpleFileVisitor<Path> {

    private final Path source;
    private final Path target;

    DirCopier(Path source, Path target) {
      this.source = source;
      this.target = target;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      final Path dest = target.resolve(source.relativize(file));
      Files.copy(file, dest, COPY_ATTRIBUTES, REPLACE_EXISTING);
      return CONTINUE;
    }

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
      Path dest = target.resolve(source.relativize(dir));
      try {
        Files.copy(dir, dest, COPY_ATTRIBUTES);
      } catch (FileAlreadyExistsException e) {
      }
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      if (exc == null) {
        Path dest = target.resolve(source.relativize(dir));
        try {
          FileTime time = Files.getLastModifiedTime(dir);
          Files.setLastModifiedTime(dest, time);
        } catch (IOException e) {
        }
      }
      return CONTINUE;
    }
  }

  static class DirCleaner extends SimpleFileVisitor<Path> {

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
      Files.delete(file);
      return CONTINUE;
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
      Files.delete(dir);
      return CONTINUE;
    }
  }

  private static final char ILLEGAL_FILENAME_CHARS_REPLACE = '-';
  private static final Character[] INVALID_FILENAME_CHARS = {
    '?', '[', ']', '/', '\\', '=', '<', '>', ':', ';', ',', '\'',
    '"', '&', '$', '#', '*', '(', ')', '|', '~', '`', '!', '{',
    '}', '%', '+', '’', '«', '»', '”', '“', 0x7F, '\n', '\r', '\t'
  };

  /**
   * Sanitizes a filename, replacing with dash
   *
   * <ul>
   *   <li>Removes special characters that are illegal in filenames on certain operating systems
   *   <li>Replaces spaces and consecutive underscore with a single dash
   *   <li>Trims dot, dash and underscore from beginning and end of filename (with and without the
   *       extension part)
   *       <ul>
   *
   * @param originalFileName The filename to be sanitized
   * @return string The sanitized filename
   */
  public static String safeFileName(String originalFileName) {
    if (StringUtils.isEmpty(originalFileName)) {
      return originalFileName;
    }
    // trim
    String fileName = originalFileName.trim();
    // unaccent
    fileName = StringUtils.stripAccent(fileName);
    // replace illegal and space
    char[] chars = fileName.toCharArray();
    for (int i = 0; i < chars.length; i++) {
      char c = chars[i];
      if (c == '\u0020') {
        chars[i] = ILLEGAL_FILENAME_CHARS_REPLACE;
        continue;
      }
      for (char illegal : INVALID_FILENAME_CHARS) {
        if (c == illegal) {
          chars[i] = ILLEGAL_FILENAME_CHARS_REPLACE;
          break;
        }
      }
    }
    fileName = new String(chars);
    // consecutive underscore
    fileName =
        fileName.replaceAll(
            "(" + ILLEGAL_FILENAME_CHARS_REPLACE + ")+",
            String.valueOf(ILLEGAL_FILENAME_CHARS_REPLACE));
    // leading and trailing dot/dash/underscore
    fileName = fileName.replaceAll("(?:^[-_.]+)|(?:[-_.]+$)", "");
    // leading and trailing dot/dash/underscore on file name only (without extension)
    if (fileName.indexOf('.') > 0) {
      String extension = getExtension(fileName);
      String fileNameWithoutExtension =
          stripExtension(fileName).replaceAll("(?:^[-_.]+)|(?:[-_.]+$)", "");
      fileName = fileNameWithoutExtension + "." + extension;
    }
    // end
    return fileName;
  }

  /**
   * Gets the extension part of the given fileName
   *
   * <p>It returns the extension of the fileName after the last dot
   *
   * @param fileName the fileName to retrieve the extension of
   * @return the extension of the file or an empty string is not found or null if the given filename
   *     is null
   */
  public static String getExtension(String fileName) {
    if (fileName == null) {
      return null;
    }

    int pos = fileName.lastIndexOf('.');

    if (pos > 0) {
      return fileName.substring(pos + 1);
    }

    return "";
  }

  /**
   * Strip the extension part of the given fileName
   *
   * <p>It returns the name of file before the last dot
   *
   * @param fileName the fileName to strip the extension
   * @return the file name
   */
  public static String stripExtension(String fileName) {
    if (fileName == null) {
      return null;
    }

    int pos = fileName.lastIndexOf('.');

    if (pos > 0) {
      return fileName.substring(0, pos);
    }

    return fileName;
  }

  /**
   * Return the name of the file
   *
   * @param filePath the file path
   * @return name of the file
   */
  public static String getFileName(String filePath) {
    if (filePath == null) {
      return null;
    }

    return getFileName(Path.of(filePath));
  }

  /**
   * Return the name of the file
   *
   * @param filePath the file path
   * @return name of the file
   */
  public static String getFileName(Path filePath) {
    if (filePath == null) {
      return null;
    }

    return filePath.getFileName().toString();
  }

  /**
   * Copy the contents of the given source file to the given destination file.
   *
   * @param source the source file to copy from
   * @param destination the destination file to copy to
   * @throws IOException in case of I/O errors
   */
  public static void copyFile(File source, File destination) throws IOException {
    if (!source.exists()) {
      return;
    }

    write(destination, new FileInputStream(source));
  }

  public static void copyPath(Path source, Path destination) throws IOException {
    if (!Files.exists(source)) {
      return;
    }

    write(destination, Files.newInputStream(source));
  }

  private static final int BUFFER_SIZE = 4096;

  /**
   * Copy the contents of the given InputStream to the given File.
   *
   * @param file the file to copy to
   * @param inputStream the inputStream to copy from
   * @throws IOException in case of I/O errors
   */
  public static void write(File file, InputStream inputStream) throws IOException {
    write(file, inputStream, false);
  }

  /**
   * Copy the contents of the given InputStream to the given File.
   *
   * @param file the file to copy to
   * @param inputStream the inputStream to copy from
   * @param append write to the end of the file rather than the beginning
   * @throws IOException in case of I/O errors
   */
  public static void write(File file, InputStream inputStream, boolean append) throws IOException {
    write(file.toPath(), inputStream, append);
  }

  /**
   * Copy the contents of the given InputStream to the given Path.
   *
   * @param path the path to copy to
   * @param inputStream the inputStream to copy from
   * @throws IOException in case of I/O errors
   */
  public static void write(Path path, InputStream inputStream) throws IOException {
    write(path, inputStream, false);
  }

  /**
   * Copy the contents of the given InputStream to the given Path.
   *
   * @param path the path to copy to
   * @param inputStream the inputStream to copy from
   * @param append write to the end of the file rather than the beginning
   * @throws IOException in case of I/O errors
   */
  public static void write(Path path, InputStream inputStream, boolean append) throws IOException {
    Files.createDirectories(path.getParent());

    try (BufferedOutputStream bos =
        new BufferedOutputStream(
            Files.newOutputStream(
                path, append ? StandardOpenOption.APPEND : StandardOpenOption.CREATE))) {
      int read = 0;
      byte[] bytes = new byte[BUFFER_SIZE];
      while ((read = inputStream.read(bytes)) != -1) {
        bos.write(bytes, 0, read);
      }
      bos.flush();
    }
  }

  /**
   * Check if the candidate path is located inside the parent path
   *
   * @param parent the parent path
   * @param candidate the candidate path
   * @return tru if the candidate path is located inside the parent path, otherwise false
   */
  public static boolean isChildPath(Path parent, Path candidate) {
    final Path normalizedParent = parent.normalize();
    final Path normalizedCandidate = normalizedParent.resolve(candidate).normalize();
    return normalizedCandidate.startsWith(normalizedParent)
        && !normalizedCandidate.equals(normalizedParent);
  }
}
