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

/**
 * The Access-Control-Expose-Headers response header allows a server to indicate
 * which response headers should be made available to scripts running in the
 * browser, in response to a cross-origin request.
 */
sealed trait AccessControlExposeHeaders

object AccessControlExposeHeaders {

  final case class AccessControlExposeHeadersValue(values: Chunk[CharSequence]) extends AccessControlExposeHeaders

  case object All extends AccessControlExposeHeaders

  case object NoHeaders extends AccessControlExposeHeaders

  def fromAccessControlExposeHeaders(accessControlExposeHeaders: AccessControlExposeHeaders): String =
    accessControlExposeHeaders match {
      case AccessControlExposeHeadersValue(value) => value.mkString(", ")
      case All                                    => "*"
      case NoHeaders                              => ""
    }

  def toAccessControlExposeHeaders(value: String): AccessControlExposeHeaders = {
    value match {
      case ""          => NoHeaders
      case "*"         => All
      case headerNames =>
        AccessControlExposeHeadersValue(
          Chunk.fromArray(
            headerNames
              .split(",")
              .map(_.trim),
          ),
        )
    }
  }

}
