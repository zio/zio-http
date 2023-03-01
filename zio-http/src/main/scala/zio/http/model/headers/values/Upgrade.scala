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

sealed trait Upgrade

object Upgrade {
  final case class UpgradeProtocols(protocols: Chunk[UpgradeValue]) extends Upgrade
  final case class UpgradeValue(protocol: String, version: String)  extends Upgrade
  case object InvalidUpgradeValue                                   extends Upgrade

  def fromUpgrade(upgrade: Upgrade): String =
    upgrade match {
      case UpgradeProtocols(protocols)     => protocols.map(fromUpgrade).mkString(", ")
      case UpgradeValue(protocol, version) => s"$protocol/$version"
      case InvalidUpgradeValue             => ""
    }

  def toUpgrade(value: String): Upgrade = {
    value.split(",").map(_.trim).toList match {
      case Nil  => InvalidUpgradeValue
      case list =>
        val protocols: List[UpgradeValue] = list.map { protocol =>
          protocol.split("/").map(_.trim).toList match {
            case Nil                        => InvalidUpgradeValue
            case protocol :: version :: Nil =>
              UpgradeValue(protocol, version)
            case _                          => InvalidUpgradeValue
          }
        }.collect { case v: UpgradeValue => v }
        UpgradeProtocols(Chunk.fromIterable(protocols))
    }

//    value.split("/").toList match {
//      case protocol :: version :: Nil => UpgradeValue(protocol, version)
//      case _                          => InvalidUpgradeValue
//    }
  }
}
