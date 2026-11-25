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

import zio.Config

/**
 * Whether and how compressed message bodies are decompressed. `No` leaves bodies as they are, while `Strict` and `NonStrict` add a content decompressor to the Netty pipeline and differ in the `strict` flag handed to it.
 */
sealed trait Decompression {
  /** Returns `true` when a content decompressor is added to the pipeline. It is `false` only for `Decompression.No`. */
  def enabled: Boolean
  /** The value passed as the `strict` argument of Netty's `HttpContentDecompressor`. It is `true` only for `Decompression.Strict`. */
  def strict: Boolean
}

/**
 * The decompression settings and a `Config` that reads them.
 */
object Decompression {
  /** Decompression is disabled. */
  case object No        extends Decompression {
    override def enabled: Boolean = false
    override def strict: Boolean  = false
  }
  /** Decompression is enabled and `strict` is `true`. */
  case object Strict    extends Decompression {
    override def enabled: Boolean = true
    override def strict: Boolean  = true
  }
  /** Decompression is enabled and `strict` is `false`. */
  case object NonStrict extends Decompression {
    override def enabled: Boolean = true
    override def strict: Boolean  = false
  }

  /**
   * A `Config` that reads one of the strings `no`, `strict` and `nonstrict`,
   * in lowercase, as `Decompression.No`, `Decompression.Strict` and
   * `Decompression.NonStrict`. Any other string, including the same names in
   * other casing, fails with a `Config.Error.InvalidData` whose message is `Invalid decompression value: ` followed by the string.
   */
  def config: Config[Decompression] =
    Config.string.mapOrFail {
      case "no"        => Right(No)
      case "strict"    => Right(Strict)
      case "nonstrict" => Right(NonStrict)
      case other       => Left(Config.Error.InvalidData(message = s"Invalid decompression value: $other"))
    }
}
