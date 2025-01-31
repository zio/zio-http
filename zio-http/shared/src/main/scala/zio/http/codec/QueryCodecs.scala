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

import zio.http.QueryParams
import zio.http.internal.{ErrorConstructor, StringSchemaCodec}

private[codec] trait QueryCodecs {

  def query[A](name: String)(implicit schema: Schema[A]): QueryCodec[A] = {
    val codec = StringSchemaCodec.fromSchema[A, QueryParams](
      schema,
      (qp, k, v) => qp.addQueryParam(k, v),
      (qp, kvs) => qp.addQueryParams(kvs),
      (qp, k) => qp.hasQueryParam(k),
      (qp, k) => qp.unsafeQueryParam(k),
      (qp, k) => qp.getAll(k),
      (qp, k) => qp.valueCount(k),
      ErrorConstructor(
        param => HttpCodecError.MissingQueryParam(param),
        params => HttpCodecError.MissingQueryParams(params),
        validationErrors => HttpCodecError.InvalidEntity.wrap(validationErrors),
        (param, value) => HttpCodecError.MalformedQueryParam(param, value),
        (param, expected, actual) => HttpCodecError.InvalidQueryParamCount(param, expected, actual),
      ),
      isKebabCase = false,
      name,
    )
    HttpCodec.Query(codec)
  }

  def queryAll[A](implicit schema: Schema[A]): QueryCodec[A] = {
    val codec = StringSchemaCodec.fromSchema[A, QueryParams](
      schema,
      (qp, k, v) => qp.addQueryParam(k, v),
      (qp, kvs) => qp.addQueryParams(kvs),
      (qp, k) => qp.hasQueryParam(k),
      (qp, k) => qp.unsafeQueryParam(k),
      (qp, k) => qp.getAll(k),
      (qp, k) => qp.valueCount(k),
      ErrorConstructor(
        param => HttpCodecError.MissingQueryParam(param),
        params => HttpCodecError.MissingQueryParams(params),
        validationErrors => HttpCodecError.InvalidEntity.wrap(validationErrors),
        (param, value) => HttpCodecError.MalformedQueryParam(param, value),
        (param, expected, actual) => HttpCodecError.InvalidQueryParamCount(param, expected, actual),
      ),
      isKebabCase = false,
      null,
    )
    HttpCodec.Query(codec)
  }

}
