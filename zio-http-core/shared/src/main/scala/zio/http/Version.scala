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

package zio.http

sealed trait Version { self =>

  /**
   * An integer representing the version. The integer is arbitrary, it is only
   * guaranteed that later versions of the HTTP protocol have a higher number,
   * and that the "default" version has the lowest number.
   */
  def ordinal: Int

  def ++(that: Version): Version =
    if (self.ordinal > that.ordinal) self else that

  def combine(that: Version): Version = self ++ that

  def isHttp1_0: Boolean = self == Version.Http_1_0

  def isHttp1_1: Boolean = self == Version.Http_1_1
}

object Version {
  val `HTTP/1.0`: Version = Http_1_0
  val `HTTP/1.1`: Version = Http_1_1

  /**
   * Indicates no preference for version. The default version will be used.
   */
  case object Default extends Version {
    val ordinal = 0
  }

  case object Http_1_0 extends Version {
    val ordinal = 1
  }

  case object Http_1_1 extends Version {
    val ordinal = 2
  }
}
