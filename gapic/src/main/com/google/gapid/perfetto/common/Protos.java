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

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gapid.proto.service.Service;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class Protos {
  public static class Row { // TS2J: sigh
    public final Map<String, Object> values = Maps.newHashMap();
  }

  public static Object getCell(Service.PerfettoQueryResult result, int column, int row) {
    Service.PerfettoQueryResult.ColumnValues values = result.getColumns(column);
    if (values.getIsNulls(row)) {
      return null;
    }
    switch (result.getColumnDescriptors(column).getType()) {
      case LONG:
        return Long.valueOf(values.getLongValues(row));
      case DOUBLE:
        return Double.valueOf(values.getDoubleValues(row));
      case STRING:
        return values.getStringValues(row);
      default:
        throw new RuntimeException("Unhandled type!");
    }
  }

  public static String[] rawQueryResultColumns(Service.PerfettoQueryResult result) {
    Set<String> uniqColNames = Sets.newHashSet();
    Set<String> colNamesToDedupe = Sets.newHashSet();
    for (Service.PerfettoQueryResult.ColumnDesc col : result.getColumnDescriptorsList()) {
      if (!uniqColNames.add(col.getName())) {
        colNamesToDedupe.add(col.getName());
      }
    }
    String[] res = new String[result.getColumnDescriptorsCount()];
    for (int i = 0; i < res.length; i++) {
      String colName = result.getColumnDescriptors(i).getName();
      if (colNamesToDedupe.contains(colName)) {
        res[i] = colName + "." + (i + 1);
      } else {
        res[i] = colName;
      }
    }
    return res;
  }

  // The suppliers are only valid between next() calls.
  // The iterator does not throw an exception in next().
  public static Iterator<Supplier<Row>> rawQueryResultIter(Service.PerfettoQueryResult result) {
    String[] colNames = rawQueryResultColumns(result);
    return new Iterator<Supplier<Row>>() {
      int row = -1;

      @Override
      public boolean hasNext() {
        return row < result.getNumRecords();
      }

      @Override
      public Supplier<Row> next() {
        row++;
        return () -> {
          Row r = new Row();
          for (int i = 0; i < result.getColumnDescriptorsCount(); i++) {
            r.values.put(colNames[i], getCell(result, i, row));
          }
          return r;
        };
      }
    };
  }

  public static long[] longValues(Service.PerfettoQueryResult result, int column) {
    long[] a = new long[(int)result.getNumRecords()];
    List<Long> values = result.getColumns(column).getLongValuesList();
    for (int i = 0; i < a.length; i++) {
      a[i] = values.get(i);
    }
    return a;
  }

  public static int[] intValues(Service.PerfettoQueryResult result, int column) {
    int[] a = new int[(int)result.getNumRecords()];
    List<Long> values = result.getColumns(column).getLongValuesList();
    for (int i = 0; i < a.length; i++) {
      a[i] = values.get(i).intValue();
    }
    return a;
  }
}
