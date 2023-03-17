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

import zio.Chunk

final case class AccessControlRequestHeaders(values: Chunk[String])

/**
 * The Access-Control-Request-Headers request header is used by browsers when
 * issuing a preflight request to let the server know which HTTP headers the
 * client might send when the actual request is made (such as with
 * setRequestHeader()). The complementary server-side header of
 * Access-Control-Allow-Headers will answer this browser-side header.
 */
object AccessControlRequestHeaders {
  def parse(values: String): Either[String, AccessControlRequestHeaders] = {
    Chunk.fromArray(values.trim().split(",")).filter(_.nonEmpty) match {
      case Chunk() => Left("AccessControlRequestHeaders cannot be empty")
      case xs      => Right(AccessControlRequestHeaders(xs))
    }
  }

  def render(headers: AccessControlRequestHeaders): String =
    headers.values.mkString(",")
}
