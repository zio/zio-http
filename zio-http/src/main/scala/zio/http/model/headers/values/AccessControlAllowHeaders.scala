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

sealed trait AccessControlAllowHeaders

/**
 * The Access-Control-Allow-Headers response header is used in response to a
 * preflight request which includes the Access-Control-Request-Headers to
 * indicate which HTTP headers can be used during the actual request.
 */
object AccessControlAllowHeaders {

  final case class Some(values: Chunk[CharSequence]) extends AccessControlAllowHeaders

  case object All extends AccessControlAllowHeaders

  case object None extends AccessControlAllowHeaders

  def fromAccessControlAllowHeaders(accessControlAllowHeaders: AccessControlAllowHeaders): String =
    accessControlAllowHeaders match {
      case Some(value) => value.mkString(", ")
      case All         => "*"
      case None        => ""
    }

  def toAccessControlAllowHeaders(value: String): Either[String, AccessControlAllowHeaders] =
    Right {
      value match {
        case ""          => None
        case "*"         => All
        case headerNames =>
          Some(
            Chunk.fromArray(
              headerNames
                .split(",")
                .map(_.trim),
            ),
          )
      }
    }

}
