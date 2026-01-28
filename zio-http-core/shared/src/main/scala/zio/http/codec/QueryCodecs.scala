/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.codec
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.schema.Schema

import zio.http.internal.{ErrorConstructor, StringSchemaCodec}

private[codec] trait QueryCodecs {

  /**
   * Retrieves the query parameter with the specified name as a value of the
   * specified type. The type must have a schema and can be a primitive type
   * (e.g. Int, String, UUID, Instant etc.), a case class with a single field or
   * a collection of either of these.
   */
  def query[A](name: String)(implicit schema: Schema[A]): QueryCodec[A] =
    HttpCodec.Query(StringSchemaCodec.queryFromSchema[A](schema, ErrorConstructor.query, name))

  /**
   * Retrieves query parameters as a value of the specified type. The type must
   * have a schema and be a case class and all fields must be query parameters.
   * So fields must be of primitive types (e.g. Int, String, UUID, Instant
   * etc.), a case class with a single field or a collection of either of these.
   * Query parameters are selected by field names.
   */
  def query[A](implicit schema: Schema[A]): QueryCodec[A] =
    HttpCodec.Query(StringSchemaCodec.queryFromSchema[A](schema, ErrorConstructor.query, null))

  @deprecated("Use query[A] instead", "3.1.0")
  def queryAll[A](implicit schema: Schema[A]): QueryCodec[A] =
    query[A]

}
