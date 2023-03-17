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

import zio.Chunk

final case class Host(hostAddress: String, port: Option[Int] = None)

object Host {
  def apply(hostAddress: String, port: Int): Host = Host(hostAddress, Some(port))

  def toHost(value: String): Either[String, Host] = {
    Chunk.fromArray(value.split(":")) match {
      case Chunk(host, portS)           =>
        Try(portS.toInt).map(port => Host(host, Some(port))).toEither.left.map(_ => "Invalid Host header")
      case Chunk(host) if host.nonEmpty =>
        Right(Host(host))
      case _                            =>
        Left("Invalid Host header")
    }
  }

  def fromHost(host: Host): String =
    host match {
      case Host(address, None)       => address
      case Host(address, Some(port)) => s"$address:$port"
    }
}
