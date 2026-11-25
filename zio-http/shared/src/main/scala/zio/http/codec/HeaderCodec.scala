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
 * Codecs for HTTP headers, to use in an `Endpoint` or another [[HttpCodec]]. It
 * has a codec for each well-known header, such as `authorization` or
 * `contentType`, which decodes the header into its typed `zio.http.Header`
 * value, and `headerAs` and `headers` for custom headers decoded with a ZIO
 * Schema. `HttpCodec` has the same members.
 *
 * {{{
 * import zio.http._
 *
 * val tenant: HeaderCodec[String]               = HeaderCodec.headerAs[String]("x-tenant")
 * val auth: HeaderCodec[Header.Authorization]   = HeaderCodec.authorization
 * val both: HeaderCodec[(String, Header.Authorization)] = tenant ++ auth
 *
 * tenant.encodeRequest("acme").headers.toList // List((x-tenant,acme))
 * }}}
 *
 * @see
 *   [[https://zio.dev/zio-http/reference/http-codec/ HttpCodec]]
 */
object HeaderCodec extends HeaderCodecs
