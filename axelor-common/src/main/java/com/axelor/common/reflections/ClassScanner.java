/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.common.reflections;

import com.google.common.reflect.ClassPath;
import com.google.common.reflect.ClassPath.ClassInfo;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

/**
 * The {@link ClassScanner} uses ASM and guava's ClassPath API to search for types based on super
 * type or annotations.
 */
final class ClassScanner {

  private static final int ASM_FLAGS =
      ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES;

  private static final String OBJECT_CLASS_NAME = "java.lang.Object";
  private static final String OBJECT_CLASS_NAME_ASM = "java/lang/Object";

  private ClassLoader loader;

  private Map<String, Collector> collectors = new ConcurrentHashMap<>();
  private Set<String> packages = new LinkedHashSet<>();
  private Set<Pattern> pathPatterns = new LinkedHashSet<>();

  /**
   * Create a new instance of {@link ClassScanner} using the given {@link ClassLoader}. <br>
   * <br>
   * The optional package names can be provided to restrict the scan within those packages.
   *
   * @param loader the {@link ClassLoader} to use for scanning
   * @param packages the package names to restrict the scan within
   */
  public ClassScanner(ClassLoader loader, String... packages) {
    this.loader = loader;
    if (packages != null) {
      for (String name : packages) {
        this.packages.add(name);
      }
    }
  }

  /**
   * Find with the given URL pattern.
   *
   * @param pattern the URL pattern
   * @return the same finder
   */
  public ClassScanner byURL(String pattern) {
    Objects.requireNonNull(pattern, "pattern must not be null");
    pathPatterns.add(Pattern.compile(pattern));
    return this;
  }

  public <T> Set<Class<? extends T>> getSubTypesOf(Class<T> type) {
    Set<Class<? extends T>> classes = new HashSet<>();
    Set<String> types;
    try {
      types = getSubTypesOf(type.getName());
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    for (String sub : types) {
      try {
        Class<?> found = loader.loadClass(sub);
        classes.add(found.asSubclass(type));
      } catch (Throwable e) {
      }
    }
    return Collections.unmodifiableSet(classes);
  }

  public Set<Class<?>> getTypesAnnotatedWith(Class<?> annotation) {
    final Set<Class<?>> classes = new HashSet<>();
    if (collectors.isEmpty()) {
      try {
        scan();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    for (String klass : collectors.keySet()) {
      Set<String> my = collectors.get(klass).annotations;
      if (my == null) {
        continue;
      }
      if (my.contains(annotation.getName())) {
        try {
          classes.add(loader.loadClass(klass));
        } catch (Throwable e) {
        }
      }
    }
    return Collections.unmodifiableSet(classes);
  }

  private Set<String> getSubTypesOf(String type) throws IOException {
    final Set<String> types = new HashSet<>();
    if (collectors.isEmpty()) {
      scan();
    }

    for (String klass : collectors.keySet()) {
      Set<String> my = collectors.get(klass).superNames;
      if (my == null) {
        continue;
      }
      if (my.contains(type)) {
        types.add(klass);
        types.addAll(getSubTypesOf(klass));
      }
    }

    return types;
  }

  private void scan() throws IOException {
    final ClassPath classPath = ClassPath.from(loader);
    final Map<String, ClassInfo> classes = new HashMap<>();

    for (ClassInfo info : classPath.getTopLevelClasses()) {
      // in case of duplicate classes, first one would win
      if (!classes.containsKey(info.getName())) {
        classes.put(info.getName(), info);
      }
    }

    if (packages.isEmpty()) {
      for (ClassInfo info : classes.values()) {
        try {
          scan(info, classes);
        } catch (ClassNotFoundException e) {
        }
      }
    } else {
      for (String pkg : packages) {
        for (ClassInfo info : classPath.getTopLevelClassesRecursive(pkg)) {
          try {
            scan(info, classes);
          } catch (ClassNotFoundException e) {
          }
        }
      }
    }
  }

  private void scan(final ClassInfo info, final Map<String, ClassInfo> classes)
      throws ClassNotFoundException {
    if (info == null
        || OBJECT_CLASS_NAME.equals(info.getName())
        || collectors.containsKey(info.getName())) {
      return;
    }

    final URL resource = info.url();
    boolean matched =
        pathPatterns.isEmpty()
            || pathPatterns.stream()
                .map(p -> p.matcher(resource.getFile()).matches())
                .findFirst()
                .orElse(false);

    if (!matched) {
      return;
    }

    try (final InputStream is = resource.openStream()) {
      final ClassReader reader = new ClassReader(is);
      final Collector collector = new Collector();
      reader.accept(collector, ASM_FLAGS);
      collectors.put(info.getName(), collector);
      if (collector.superNames != null) {
        for (String base : collector.superNames) {
          scan(classes.get(base), classes);
        }
      }
    } catch (IOException e) {
      throw new ClassNotFoundException(info.getName());
    }
  }

  private static class Collector extends ClassVisitor {

    private Set<String> superNames;
    private Set<String> annotations;

    public Collector() {
      super(Opcodes.ASM9);
    }

    private void acceptSuper(String name) {
      if (name == null) {
        return;
      }
      if (superNames == null) {
        superNames = new HashSet<>();
      }
      superNames.add(name.replace("/", "."));
    }

    private void acceptAnnotation(String name) {
      if (name == null || OBJECT_CLASS_NAME_ASM.equals(name)) {
        return;
      }
      if (annotations == null) {
        annotations = new HashSet<>();
      }
      annotations.add(name.replace("/", ".").substring(1, name.length() - 1));
    }

    @Override
    public void visit(
        int version,
        int access,
        String name,
        String signature,
        String superName,
        String[] interfaces) {
      acceptSuper(superName);
      if (interfaces != null) {
        for (String iface : interfaces) {
          acceptSuper(iface);
        }
      }
    }

    @Override
    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
      acceptAnnotation(desc);
      return null;
    }
  }
}
