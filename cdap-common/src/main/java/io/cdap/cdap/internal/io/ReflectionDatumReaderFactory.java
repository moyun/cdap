/*
 * Copyright © 2014-2017 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package io.cdap.cdap.internal.io;

import io.cdap.cdap.api.data.schema.Schema;
import io.cdap.cdap.common.io.DatumReader;
import com.google.common.reflect.TypeToken;

/**
 * A {@link DatumReaderFactory} that creates {@link ReflectionDatumReader}.
 */
public final class ReflectionDatumReaderFactory implements DatumReaderFactory {

  @Override
  public <T> DatumReader<T> create(TypeToken<T> type, Schema schema) {
    return new ReflectionDatumReader<>(schema, type);
  }
}