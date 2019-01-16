/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.gapid.perfetto.common;

import static com.google.gapid.util.MoreFutures.logFailure;
import static com.google.gapid.util.MoreFutures.transform;
import static com.google.gapid.util.Scheduler.EXECUTOR;
import static com.google.gapid.widgets.Widgets.scheduleIfNotDisposed;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gapid.proto.service.Service;
import com.google.gapid.proto.service.Service.PerfettoQueryResult;
import com.google.gapid.proto.service.path.Path;
import com.google.gapid.server.Client;
import com.google.gapid.views.StatusBar;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public interface Engine {
  public ListenableFuture<Service.PerfettoQueryResult> query(String sqlQuery);

  public default ListenableFuture<long[]> queryOneRow(String query) {
    return transform(query(query), result -> {
      long[] res = new long[result.getColumnDescriptorsCount()];
      for (int i = 0; i < res.length; i++) {
        res[i] = result.getColumns(i).getLongValues(0);
      }
      return res;
    });
  }

  //TODO(hjd): Maybe we should cache result? But then Engine must be streaming aware.
  public default ListenableFuture<Integer> getNumberOfCpus() {
    return transform(query("select count(distinct(cpu)) as cpuCount from sched"), result ->
      (int)result.getColumns(0).getLongValues(0));
  }

  public default ListenableFuture<Integer> getNumberOfProcesses() {
    return transform(query("select count(*) from process"), result ->
      (int)result.getColumns(0).getLongValues(0));
  }

  public default ListenableFuture<TimeSpan> getTraceTimeBounds() {
    return transform(queryOneRow("select start_ts, end_ts from trace_bounds"), result ->
      new TimeSpan(result[0] / 1.0e9, result[1] / 1.0e9));
  }

  public static class GapisEngine implements Engine {
    private static final Logger LOG = Logger.getLogger(GapisEngine.class.getName());

    private final StatusBar status;
    private final Client client;
    private final Path.Capture capture;
    private final AtomicInteger scheduled = new AtomicInteger(0);
    private final AtomicInteger done = new AtomicInteger(0);
    private final AtomicBoolean updating = new AtomicBoolean(false);

    public GapisEngine(StatusBar status, Client client, Path.Capture capture) {
      this.status = status;
      this.client = client;
      this.capture = capture;
    }

    @Override
    public ListenableFuture<PerfettoQueryResult> query(String sqlQuery) {
      scheduled.incrementAndGet();
      updateStatus();
      return transform(client.perfettoQuery(capture, sqlQuery), r -> {
        done.incrementAndGet();
        updateStatus();
        return r;
      });
    }

    private void updateStatus() {
      if (updating.compareAndSet(false, true)) {
        scheduleIfNotDisposed(status, () -> {
          updating.set(false);
          int d = done.get(), s = scheduled.get();
          if (s == 0) {
            status.setServerStatusPrefix("");
          } else {
            status.setServerStatusPrefix("Queries: " + d + "/" + s);
          }

          if (s != 0 && d == s) {
            logFailure(LOG, EXECUTOR.schedule(() -> {
              int dd = done.get();
              if (scheduled.compareAndSet(dd, 0)) {
                done.updateAndGet(x -> x - dd);
                updateStatus();
              }
            }, 250, MILLISECONDS));
          }
        });
      }
    }
  }
}
