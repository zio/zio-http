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

sealed trait AccessControlAllowCredentials

object AccessControlAllowCredentials {

  /**
   * The Access-Control-Allow-Credentials header is sent in response to a
   * preflight request which includes the Access-Control-Request-Headers to
   * indicate whether or not the actual request can be made using credentials.
   */
  case object AllowCredentials extends AccessControlAllowCredentials

  /**
   * The Access-Control-Allow-Credentials header is not sent in response to a
   * preflight request.
   */
  case object DoNotAllowCredentials extends AccessControlAllowCredentials

  def fromAccessControlAllowCredentials(
    accessControlAllowCredentials: AccessControlAllowCredentials,
  ): String =
    accessControlAllowCredentials match {
      case AllowCredentials      => "true"
      case DoNotAllowCredentials => "false"
    }

  def toAccessControlAllowCredentials(value: String): AccessControlAllowCredentials =
    value match {
      case "true"  => AllowCredentials
      case "false" => DoNotAllowCredentials
      case _       => DoNotAllowCredentials
    }

  def toAccessControlAllowCredentials(value: Boolean): AccessControlAllowCredentials =
    value match {
      case true  => AllowCredentials
      case false => DoNotAllowCredentials
    }
}
