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

package zio.http.model

sealed trait Version { self =>
  def ++(that: Version): Version = (self, that) match {
    case (Version.Http_1_0, Version.Http_1_0) => Version.Http_1_0
    case _                                    => Version.Http_1_1
  }

  def combine(that: Version): Version = self ++ that

  def isHttp1_0: Boolean = self == Version.Http_1_0

  def isHttp1_1: Boolean = self == Version.Http_1_1

}

object Version {
  val `HTTP/1.0`: Version = Http_1_0
  val `HTTP/1.1`: Version = Http_1_1

  case object Http_1_0 extends Version

  case object Http_1_1 extends Version
}
