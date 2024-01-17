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

sealed trait Decompression {
  def enabled: Boolean
  def strict: Boolean
}

object Decompression {
  case object No        extends Decompression {
    override def enabled: Boolean = false
    override def strict: Boolean  = false
  }
  case object Strict    extends Decompression {
    override def enabled: Boolean = true
    override def strict: Boolean  = true
  }
  case object NonStrict extends Decompression {
    override def enabled: Boolean = true
    override def strict: Boolean  = false
  }

  lazy val config: Config[Decompression] =
    Config.string.mapOrFail {
      case "no"        => Right(No)
      case "strict"    => Right(Strict)
      case "nonstrict" => Right(NonStrict)
      case other       => Left(Config.Error.InvalidData(message = s"Invalid decompression value: $other"))
    }
}
