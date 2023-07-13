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

import zio.Unsafe

sealed trait Scheme { self =>
  def encode: String = self match {
    case Scheme.HTTP  => "http"
    case Scheme.HTTPS => "https"
    case Scheme.WS    => "ws"
    case Scheme.WSS   => "wss"
  }

  def isHttp: Boolean = !isWebSocket

  def isWebSocket: Boolean = self match {
    case Scheme.WS  => true
    case Scheme.WSS => true
    case _          => false
  }

  def isSecure: Boolean = self match {
    case Scheme.HTTPS => true
    case Scheme.WSS   => true
    case _            => false
  }

  def defaultPort: Int = self match {
    case Scheme.HTTP  => 80
    case Scheme.HTTPS => 443
    case Scheme.WS    => 80
    case Scheme.WSS   => 443
  }
}
object Scheme       {

  /**
   * Decodes a string to an Option of Scheme. Returns None in case of
   * null/non-valid Scheme
   */
  def decode(scheme: String): Option[Scheme] =
    Option(unsafe.decode(scheme)(Unsafe.unsafe))

  private[zio] object unsafe {
    def decode(scheme: String)(implicit unsafe: Unsafe): Scheme = {
      if (scheme == null) null
      else if (scheme.equalsIgnoreCase("HTTPS")) Scheme.HTTPS
      else if (scheme.equalsIgnoreCase("HTTP")) Scheme.HTTP
      else if (scheme.equalsIgnoreCase("WSS")) Scheme.WSS
      else if (scheme.equalsIgnoreCase("WS")) Scheme.WS
      else null
    }
  }

  case object HTTP extends Scheme

  case object HTTPS extends Scheme

  case object WS extends Scheme

  case object WSS extends Scheme
}
