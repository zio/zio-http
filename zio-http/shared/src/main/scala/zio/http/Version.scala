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

/**
 * The version of the HTTP protocol of a request or response. `Version.Default`
 * means no preference.
 */
sealed trait Version { self =>

  /**
   * An integer representing the version. The integer is arbitrary, it is only
   * guaranteed that later versions of the HTTP protocol have a higher number,
   * and that the "default" version has the lowest number.
   */
  def ordinal: Int

  /**
   * Returns the version with the higher `ordinal`, which is `that` when both
   * have the same `ordinal`.
   */
  def ++(that: Version): Version =
    if (self.ordinal > that.ordinal) self else that

  /** Returns `this ++ that`. */
  def combine(that: Version): Version = self ++ that

  /** Returns `true` for `Version.Http_1_0`. */
  def isHttp1_0: Boolean = self == Version.Http_1_0

  /** Returns `true` for `Version.Http_1_1`. */
  def isHttp1_1: Boolean = self == Version.Http_1_1
}

/**
 * The HTTP versions that the library models.
 */
object Version {

  /** An alias of `Version.Http_1_0`. */
  val `HTTP/1.0`: Version = Http_1_0

  /** An alias of `Version.Http_1_1`. */
  val `HTTP/1.1`: Version = Http_1_1

  /**
   * Indicates no preference for version. The default version will be used.
   */
  case object Default extends Version {

    /** `0`, the lowest value. */
    val ordinal = 0
  }

  /** HTTP version 1.0, with `ordinal` 1. */
  case object Http_1_0 extends Version {

    /** `1`. */
    val ordinal = 1
  }

  /** HTTP version 1.1, with `ordinal` 2. */
  case object Http_1_1 extends Version {

    /** `2`. */
    val ordinal = 2
  }
}
