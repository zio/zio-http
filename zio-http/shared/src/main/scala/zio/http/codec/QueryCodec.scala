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
 * Codecs for URL query parameters, to use in an `Endpoint` or another
 * [[HttpCodec]]. `query[A](name)` reads one parameter, and `query[A]` reads
 * several parameters into the fields of a case class; the values are decoded
 * with the ZIO Schema of `A`. `HttpCodec` has the same members.
 *
 * {{{
 * import zio.http._
 *
 * val search: QueryCodec[(String, Option[Int])] =
 *   QueryCodec.query[String]("q") ++ QueryCodec.query[Option[Int]]("limit")
 *
 * search.encodeRequest(("zio", Some(10))).url.encode // ?q=zio&limit=10
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/http-codec/ HttpCodec]]
 */
object QueryCodec extends QueryCodecs
