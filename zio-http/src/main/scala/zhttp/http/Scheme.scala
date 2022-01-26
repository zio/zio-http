package zhttp.http

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme

sealed trait Scheme { self =>
  def encode: String = Scheme.asString(self)
}
object Scheme       {
  case object HTTP  extends Scheme
  case object HTTPS extends Scheme
  case object WS    extends Scheme
  case object WSS   extends Scheme

  def asString(self: Scheme): String = self match {
    case HTTP  => "http"
    case HTTPS => "https"
    case WS    => "ws"
    case WSS   => "wss"
  }

  def fromString(scheme: String): Option[Scheme] =
    scheme.toUpperCase match {
      case "HTTPS" => Option(HTTPS)
      case "HTTP"  => Option(HTTP)
      case "WS"    => Option(WS)
      case "WSS"   => Option(WSS)
      case _       => None
    }

  def fromJHttpScheme(scheme: HttpScheme): Option[Scheme] = scheme match {
    case HttpScheme.HTTPS => Option(HTTPS)
    case HttpScheme.HTTP  => Option(HTTP)
    case _                => None
  }

  def asJHttpScheme(scheme: Scheme): Option[HttpScheme] = scheme match {
    case HTTP  => Option(HttpScheme.HTTP)
    case HTTPS => Option(HttpScheme.HTTPS)
    case _     => None
  }

  def fromJWebSocketScheme(scheme: WebSocketScheme): Option[Scheme] = scheme match {
    case WebSocketScheme.WSS => Option(WSS)
    case WebSocketScheme.WS  => Option(WS)
    case _                   => None
  }

  def asJWebSocketScheme(scheme: Scheme): Option[WebSocketScheme] = scheme match {
    case WS  => Option(WebSocketScheme.WS)
    case WSS => Option(WebSocketScheme.WSS)
    case _   => None
  }
}
