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

/**
 * The Access-Control-Allow-Origin response header indicates whether the
 * response can be shared with requesting code from the given origin.
 *
 * For requests without credentials, the literal value "*" can be specified as a
 * wildcard; the value tells browsers to allow requesting code from any origin
 * to access the resource. Attempting to use the wildcard with credentials
 * results in an error.
 *
 * <origin> Specifies an origin. Only a single origin can be specified. If the
 * server supports clients from multiple origins, it must return the origin for
 * the specific client making the request.
 *
 * null Specifies the origin "null".
 */
final case class AccessControlAllowOrigin(origin: String)

object AccessControlAllowOrigin {

  def fromAccessControlAllowOrigin(accessControlAllowOrigin: AccessControlAllowOrigin): String =
    accessControlAllowOrigin.origin

  def toAccessControlAllowOrigin(value: String): Either[String, AccessControlAllowOrigin] = {
    if (value == "null" || value == "*") {
      Right(AccessControlAllowOrigin(value))
    } else {
      URL.fromString(value) match {
        case Left(exception)                                      =>
          Left(s"Invalid Access-Control-Allow-Origin: ${exception.getMessage}")
        case Right(url) if url.host.isEmpty || url.scheme.isEmpty =>
          Left(s"Invalid Access-Control-Allow-Origin: host or scheme is empty")
        case Right(_)                                             =>
          Right(AccessControlAllowOrigin(value))
      }
    }

  }
}
