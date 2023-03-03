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

/**
 * Server header value.
 */
sealed trait Server

object Server {

  /**
   * A server value with a name
   */
  final case class ServerName(name: String) extends Server

  /**
   * No server name
   */
  object EmptyServerName extends Server

  def fromServer(server: Server): String =
    server match {
      case ServerName(name) => name
      case EmptyServerName  => ""
    }

  def toServer(value: String): Server = {
    val serverTrim = value.trim
    if (serverTrim.isEmpty) EmptyServerName else ServerName(serverTrim)
  }
}
