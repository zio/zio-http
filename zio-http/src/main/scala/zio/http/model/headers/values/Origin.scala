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

package zio.http.model.headers.values

import zio.http.URL

/** Origin header value. */
sealed trait Origin

object Origin {

  /** The Origin header value is privacy sensitive or is an opaque origin. */
  case object OriginNull extends Origin

  /** The Origin header value contains scheme, host and maybe port. */
  final case class OriginValue(scheme: String, host: String, port: Option[Int]) extends Origin

  /** The Origin header value is invalid. */
  case object InvalidOriginValue extends Origin

  def fromOrigin(origin: Origin): String = {
    origin match {
      case OriginNull                           => "null"
      case OriginValue(scheme, host, maybePort) =>
        maybePort match {
          case Some(port) => s"$scheme://$host:$port"
          case None       => s"$scheme://$host"
        }
      case InvalidOriginValue                   => ""
    }
  }

  def toOrigin(value: String): Origin =
    if (value == "null") OriginNull
    else
      URL.fromString(value) match {
        case Left(_)                                              => InvalidOriginValue
        case Right(url) if url.host.isEmpty || url.scheme.isEmpty => InvalidOriginValue
        case Right(url) => OriginValue(url.scheme.get.encode, url.host.get, url.portIfNotDefault)
      }
}
