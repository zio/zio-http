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

/**
 * Phantom types that tag which parts of an HTTP message an [[HttpCodec]] reads
 * and writes. They appear as the first type parameter of `HttpCodec`, for
 * example `HttpCodec[HttpCodecType.Query, Int]` (a `QueryCodec[Int]`). They
 * have no values.
 */
sealed trait HttpCodecType

/** The phantom types used as the first type parameter of [[HttpCodec]]. */
object HttpCodecType {

  /**
   * The parts a request codec may use: path, content, query, header and method.
   */
  type RequestType <: Path with Content with Query with Header with Method

  /** The parts a response codec may use: content, header and status. */
  type ResponseType <: Content with Header with Status

  /** A codec for the path and the query string of a request. */
  type PathQuery = HttpCodecType.Path with HttpCodecType.Query

  /** Tags a codec for the path of a request. */
  type Path <: HttpCodecType

  /** Tags a codec for the body of a request or response. */
  type Content <: HttpCodecType

  /** Tags a codec for the query parameters of a request. */
  type Query <: HttpCodecType

  /** Tags a codec for the headers of a request or response. */
  type Header <: HttpCodecType

  /** Tags a codec for the method of a request. */
  type Method <: HttpCodecType

  /** Tags a codec for the status of a response. */
  type Status <: HttpCodecType

}
