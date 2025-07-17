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
package com.axelor.event;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.axelor.test.GuiceExtension;
import com.axelor.test.GuiceModules;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(GuiceExtension.class)
@GuiceModules({EventModule.class, TestModule.class})
public class TestEventsParallel {

  private static final AtomicInteger count = new AtomicInteger();

  @Inject private Event<ParallelEvent> parallelEvent;

  void onParallel0(@Observes @Named("parallel") ParallelEvent event) {
    count.incrementAndGet();
  }

  void onParallel1(@Observes @Named("parallel") ParallelEvent event) {
    count.incrementAndGet();
  }

  void onParallel2(@Observes @Named("parallel") ParallelEvent event) {
    count.incrementAndGet();
  }

  void onParallel3(@Observes @Named("parallel") ParallelEvent event) {
    count.incrementAndGet();
  }

  @Test
  public void testParallel() {
    final int numWorkers = 4;
    final int numIterations = 4;
    final int numObservers = 4;
    final ExecutorService pool = Executors.newFixedThreadPool(numWorkers);

    for (int i = 0; i < numIterations; ++i) {
      pool.execute(
          () -> parallelEvent.select(NamedLiteral.of("parallel")).fire(new ParallelEvent()));
    }

    shutdownAndAwaitTermination(pool);
    assertEquals(numIterations * numObservers, count.getAndUpdate(n -> 0));
  }

  private static void shutdownAndAwaitTermination(ExecutorService pool) {
    pool.shutdown();

    try {
      while (!pool.awaitTermination(1, TimeUnit.HOURS)) {
        System.err.printf("Pool %s takes a long time to terminate.%n", pool);
      }
    } catch (InterruptedException e) {
      pool.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private static class ParallelEvent {}
}
