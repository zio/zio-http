package zhttp.http

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme
import zhttp.http.Scheme.{HTTP, HTTPS, WS, WSS}

sealed trait Scheme { self =>
  def encode: String = self match {
    case HTTP  => "http"
    case HTTPS => "https"
    case WS    => "ws"
    case WSS   => "wss"
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

  def toJHttpScheme: Option[HttpScheme] = self match {
    case HTTP  => Option(HttpScheme.HTTP)
    case HTTPS => Option(HttpScheme.HTTPS)
    case _     => None
  }

  def toJWebSocketScheme: Option[WebSocketScheme] = self match {
    case WS  => Option(WebSocketScheme.WS)
    case WSS => Option(WebSocketScheme.WSS)
    case _   => None
  }
}
object Scheme       {

  /**
   * Decodes a string to an Option of Scheme. Returns None in case of
   * null/non-valid Scheme
   */
  def decode(scheme: String): Option[Scheme] =
    Option(unsafeDecode(scheme))

  private[zhttp] def unsafeDecode(scheme: String): Scheme = {
    if (scheme == null) null
    else
      scheme.length match {
        case 5 => HTTPS
        case 4 => HTTP
        case 3 => WSS
        case 2 => WS
        case _ => null
      }
  }

  def fromJScheme(scheme: HttpScheme): Option[Scheme] = scheme match {
    case HttpScheme.HTTPS => Option(HTTPS)
    case HttpScheme.HTTP  => Option(HTTP)
    case _                => None
  }

  def fromJScheme(scheme: WebSocketScheme): Option[Scheme] = scheme match {
    case WebSocketScheme.WSS => Option(WSS)
    case WebSocketScheme.WS  => Option(WS)
    case _                   => None
  }

  case object HTTP extends Scheme

  case object HTTPS extends Scheme

  case object WS extends Scheme

  case object WSS extends Scheme
}
