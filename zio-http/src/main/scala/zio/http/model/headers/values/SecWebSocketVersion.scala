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

sealed trait SecWebSocketVersion

/**
 * The Sec-WebSocket-Version header field is used in the WebSocket opening
 * handshake. It is sent from the client to the server to indicate the protocol
 * version of the connection.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Version
 */
object SecWebSocketVersion {
  // https://www.iana.org/assignments/websocket/websocket.xml#version-number

  final case class Version(version: Int) extends SecWebSocketVersion
  case object InvalidVersion             extends SecWebSocketVersion

  def toSecWebSocketVersion(version: String): SecWebSocketVersion =
    try {
      val v = version.toInt
      if (v >= 0 && v <= 13) Version(v)
      else InvalidVersion
    } catch {
      case _: Throwable => InvalidVersion
    }

  def fromSecWebSocketVersion(version: SecWebSocketVersion): String = version match {
    case Version(version) => version.toString
    case InvalidVersion   => ""
  }

}
