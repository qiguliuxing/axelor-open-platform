/*
 * SPDX-FileCopyrightText: Axelor <https://axelor.com>
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package com.axelor.event;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.LinkedKeyBinding;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Singleton
class EventBus {

  private final Injector injector;

  private final AtomicReference<Map<Class<?>, List<Observer>>> observersRef =
      new AtomicReference<>();

  private final LoadingCache<Class<?>, Map<Entry<Type, Set<Annotation>>, List<Observer>>>
      observersCache = Caffeine.newBuilder().weakKeys().build(k -> new ConcurrentHashMap<>());

  @Inject
  public EventBus(Injector injector) {
    this.injector = injector;
  }

  private Map<Class<?>, List<Observer>> findObservers() {
    final Map<Class<?>, List<Observer>> observers = new HashMap<>();
    injector.getAllBindings().entrySet().stream()
        .filter(entry -> !(entry.getValue() instanceof LinkedKeyBinding))
        .map(Entry::getKey)
        .map(Key::getTypeLiteral)
        .map(TypeLiteral::getRawType)
        .flatMap(t -> Arrays.stream(t.getDeclaredMethods()))
        .filter(Observer::isObserver)
        .map(Observer::new)
        .forEach(o -> observers.computeIfAbsent(o.eventRawType, k -> new ArrayList<>()).add(o));
    observers.values().forEach(items -> Collections.sort(items, Observer::compareTo));
    return observers;
  }

  private List<Observer> find(Class<?> runtimeType, Type eventType, Set<Annotation> qualifiers) {
    final List<Observer> found =
        observersRef
            .updateAndGet(observers -> observers != null ? observers : findObservers())
            .getOrDefault(runtimeType, Collections.emptyList());
    final Set<Annotation> annotations =
        Optional.ofNullable(qualifiers).orElse(Collections.emptySet());

    return found.stream()
        .filter(o -> o.matches(eventType, annotations))
        .collect(Collectors.toList());
  }

  public void fire(Object event, Type eventType, Set<Annotation> qualifiers) {
    final Class<?> eventClass = event.getClass();
    final Map<Entry<Type, Set<Annotation>>, List<Observer>> observersByTypeAndQualifiers =
        observersCache.get(eventClass);
    final List<Observer> foundObservers =
        observersByTypeAndQualifiers.computeIfAbsent(
            new SimpleImmutableEntry<>(eventType, qualifiers),
            k -> find(eventClass, k.getKey(), k.getValue()));
    foundObservers.forEach(o -> o.invoke(event));
  }
}
