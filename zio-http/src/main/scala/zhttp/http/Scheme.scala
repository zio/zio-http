package zhttp.http

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme

sealed trait Scheme { self =>
  def asString: String = Scheme.asString(self)
}
object Scheme       {
  def asString(self: Scheme): String = self match {
    case HTTP  => "http"
    case HTTPS => "https"
    case WS    => "ws"
    case WSS   => "wss"
  }

  case object HTTP  extends Scheme
  case object HTTPS extends Scheme
  case object WS    extends Scheme
  case object WSS   extends Scheme

  def fromJHttpScheme(scheme: HttpScheme): Option[Scheme] =
    scheme match {
      case HttpScheme.HTTPS => Option(HTTPS)
      case HttpScheme.HTTP  => Option(HTTP)
      case _                => None
    }

  def fromJWebSocketScheme(scheme: WebSocketScheme): Option[Scheme] = scheme match {
    case WebSocketScheme.WSS => Option(WSS)
    case WebSocketScheme.WS  => Option(WS)
    case _                   => None
  }

  def fromString(scheme: String): Option[Scheme] =
    scheme.toUpperCase match {
      case "HTTPS" => Option(HTTPS)
      case "HTTP"  => Option(HTTP)
      case "WS"    => Option(WS)
      case "WSS"   => Option(WSS)
      case _       => None
    }
}
