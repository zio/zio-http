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

/**
 * The scheme of a URL, such as `http`, `https`, `ws` or `wss`. Any other scheme is a `Scheme.Custom`.
 *
 * {{{
 * val scheme: Option[Scheme] = Scheme.decode("wss")
 * val secure                 = scheme.flatMap(_.isSecure) // Some(true)
 * val port                   = scheme.flatMap(_.defaultPort) // Some(443)
 * }}}
 */
sealed trait Scheme { self =>
  /** Returns the scheme as a string: `http`, `https`, `ws` or `wss` for the predefined schemes, and the stored text for a `Scheme.Custom`. */
  def encode: String = self match {
    case Scheme.HTTP           => "http"
    case Scheme.HTTPS          => "https"
    case Scheme.WS             => "ws"
    case Scheme.WSS            => "wss"
    case Scheme.Custom(scheme) => scheme
  }

  /** Returns `true` for `Scheme.HTTP` and `Scheme.HTTPS`. */
  def isHttp: Boolean = self match {
    case Scheme.HTTP | Scheme.HTTPS => true
    case _                          => false
  }

  /** Returns `true` for `Scheme.WS` and `Scheme.WSS`. */
  def isWebSocket: Boolean = self match {
    case Scheme.WS  => true
    case Scheme.WSS => true
    case _          => false
  }

  /** Returns `Some(true)` for `Scheme.HTTPS` and `Scheme.WSS`, `Some(false)` for `Scheme.HTTP` and `Scheme.WS`, and `None` for a `Scheme.Custom`. */
  def isSecure: Option[Boolean] = self match {
    case Scheme.HTTPS | Scheme.WSS => Some(true)
    case Scheme.HTTP | Scheme.WS   => Some(false)
    case _                         => None
  }

  /** Returns the default port for `Scheme.HTTP` (80), `Scheme.HTTPS` (443), `Scheme.WS` (80) and `Scheme.WSS` (443), and `None` for a `Scheme.Custom`. */
  def defaultPort: Option[Int] = self match {
    case Scheme.HTTP      => Some(Scheme.defaultPortForHTTP)
    case Scheme.HTTPS     => Some(Scheme.defaultPortForHTTPS)
    case Scheme.WS        => Some(Scheme.defaultPortForWS)
    case Scheme.WSS       => Some(Scheme.defaultPortForWSS)
    case Scheme.Custom(_) => None
  }

}

/**
 * Predefined schemes and decoding of scheme strings.
 */
object Scheme {

  /**
   * Decodes a string to an Option of Scheme. Returns `None` when the string is
   * `null` or empty. The exact lowercase strings `http`, `https`, `ws` and
   * `wss` give the predefined schemes. Any other string gives a
   * `Scheme.Custom` holding the string in lowercase, without checking its
   * characters, so `HTTP` gives a `Scheme.Custom` with the text `http`, not
   * `Scheme.HTTP`.
   *
   * The scheme should be lowercase and follow this syntax:
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

  /** The `http` scheme, with default port 80. */
  case object HTTP extends Scheme

  /** The `https` scheme, with default port 443. */
  case object HTTPS extends Scheme

  /** The `ws` scheme, with default port 80. */
  case object WS extends Scheme

  /** The `wss` scheme, with default port 443. */
  case object WSS extends Scheme

  /**
   * A scheme other than the four predefined ones. It has no default port, and
   * `isSecure` is `None`.
   *
   * @param scheme
   *   value MUST not be "http" "https" "ws" "wss"
   */
  sealed abstract case class Custom private[http] (scheme: String) extends Scheme

  /** The default port of `Scheme.HTTP`: 80. */
  def defaultPortForHTTP  = 80
  /** The default port of `Scheme.HTTPS`: 443. */
  def defaultPortForHTTPS = 443
  /** The default port of `Scheme.WS`: 80. */
  def defaultPortForWS    = 80
  /** The default port of `Scheme.WSS`: 443. */
  def defaultPortForWSS   = 443
}
