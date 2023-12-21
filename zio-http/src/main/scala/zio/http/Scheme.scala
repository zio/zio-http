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
import zio.stacktracer.TracingImplicits.disableAutoTrace

sealed trait Scheme { self =>
  def encode: String = self match {
    case Scheme.HTTP           => "http"
    case Scheme.HTTPS          => "https"
    case Scheme.WS             => "ws"
    case Scheme.WSS            => "wss"
    case Scheme.Custom(scheme) => scheme
  }

  def isHttp: Boolean = self match {
    case Scheme.HTTP | Scheme.HTTPS => true
    case _                          => false
  }

  def isWebSocket: Boolean = self match {
    case Scheme.WS  => true
    case Scheme.WSS => true
    case _          => false
  }

  def isSecure: Option[Boolean] = self match {
    case Scheme.HTTPS | Scheme.WSS => Some(true)
    case Scheme.HTTP | Scheme.WS   => Some(false)
    case _                         => None
  }

  /** default ports is only define for the Schemes: http, https, ws, wss */
  def defaultPort: Option[Int] = self match {
    case Scheme.HTTP      => Some(Scheme.defaultPortForHTTP)
    case Scheme.HTTPS     => Some(Scheme.defaultPortForHTTPS)
    case Scheme.WS        => Some(Scheme.defaultPortForWS)
    case Scheme.WSS       => Some(Scheme.defaultPortForWSS)
    case Scheme.Custom(_) => None
  }

}

object Scheme {

  /**
   * Decodes a string to an Option of Scheme. Returns None in case of
   * null/non-valid Scheme
   *
   * The should be lowercase and follow this syntax:
   *   - Scheme = ALPHA *( ALPHA / DIGIT / "+" / "-" / "." )
   */
  def decode(scheme: String): Option[Scheme] =
    Option(unsafe.decode(scheme)(Unsafe.unsafe))

  private[zio] object unsafe {
    def decode(scheme: String)(implicit unsafe: Unsafe): Scheme = {
      if (scheme == null || scheme.isEmpty) null
      else
        scheme match {
          case "http"  => HTTP
          case "https" => HTTPS
          case "ws"    => WS
          case "wss"   => WSS
          case custom  => new Custom(custom.toLowerCase) {}
        }
    }
  }

  case object HTTP extends Scheme

  case object HTTPS extends Scheme

  case object WS extends Scheme

  case object WSS extends Scheme

  /**
   * @param scheme
   *   value MUST not be "http" "https" "ws" "wss"
   */
  sealed abstract case class Custom private[http] (scheme: String) extends Scheme

  def defaultPortForHTTP  = 80
  def defaultPortForHTTPS = 443
  def defaultPortForWS    = 80
  def defaultPortForWSS   = 443
}
