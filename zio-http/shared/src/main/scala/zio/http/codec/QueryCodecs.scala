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
import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.schema.Schema
import zio.schema.codec.DecodeError
import zio.schema.validation.ValidationError

import zio.http.internal.{ErrorConstructor, StringSchemaCodec}

private[codec] trait QueryCodecs {

  private val errorConstructor = new ErrorConstructor {
    override def missing(fieldName: String): HttpCodecError =
      HttpCodecError.MissingQueryParam(fieldName)

    override def missingAll(fieldNames: Chunk[String]): HttpCodecError =
      HttpCodecError.MissingQueryParams(fieldNames)

    override def invalid(errors: Chunk[ValidationError]): HttpCodecError =
      HttpCodecError.InvalidEntity.wrap(errors)

    override def malformed(fieldName: String, error: DecodeError): HttpCodecError =
      HttpCodecError.MalformedQueryParam(fieldName, error)

    override def invalidCount(fieldName: String, expected: Int, actual: Int): HttpCodecError =
      HttpCodecError.InvalidQueryParamCount(fieldName, expected, actual)
  }

  def query[A](name: String)(implicit schema: Schema[A]): QueryCodec[A] =
    HttpCodec.Query(StringSchemaCodec.queryFromSchema[A](schema, errorConstructor, name))

  def queryAll[A](implicit schema: Schema[A]): QueryCodec[A] =
    HttpCodec.Query(StringSchemaCodec.queryFromSchema[A](schema, errorConstructor, null))

}
