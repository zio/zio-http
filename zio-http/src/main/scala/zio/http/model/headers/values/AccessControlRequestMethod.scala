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

import scala.util.Try

import zio.http.model.Method

final case class AccessControlRequestMethod(method: Method)

object AccessControlRequestMethod {

  def toAccessControlRequestMethod(value: String): Either[String, AccessControlRequestMethod] = {
    val method = Method.fromString(value)
    if (method == Method.CUSTOM(value)) Left(s"Invalid Access-Control-Request-Method")
    else Right(AccessControlRequestMethod(method))
  }

  def fromAccessControlRequestMethod(requestMethod: AccessControlRequestMethod): String =
    requestMethod.method.name
}
