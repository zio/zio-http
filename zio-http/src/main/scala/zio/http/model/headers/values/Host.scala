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

sealed trait Host
object Host {
  final case class HostValue(hostAddress: String, port: Option[Int] = None) extends Host
  object HostValue {
    def apply(hostAddress: String, port: Int): HostValue = HostValue(hostAddress, Some(port))
  }
  case object EmptyHostValue extends Host
  case object InvalidHostValue extends Host

  private def parse(value: String): Host = {
    value.split(":").toList match {
      case host :: portS :: Nil => Try(portS.toInt).fold(_ => InvalidHostValue, port => HostValue(host, Some(port)))
      case host :: Nil          => HostValue(host)
      case _                    => InvalidHostValue
    }
  }

  def fromHost(host: Host): String =
    host match {
      case HostValue(address, None)          => address
      case HostValue(address, Some(port))    => s"$address:$port"
      case EmptyHostValue | InvalidHostValue => ""
    }

  def toHost(value: String): Host =
    if (value.isEmpty) EmptyHostValue else parse(value)
}
