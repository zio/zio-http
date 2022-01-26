package zhttp.http

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme
import zhttp.http.Scheme.{HTTP, HTTPS, WS, WSS}

sealed trait Scheme { self =>
  def encode: String =
    self match {
      case HTTP  => "http"
      case HTTPS => "https"
      case WS    => "ws"
      case WSS   => "wss"
    }

  def toJHttpScheme: Option[HttpScheme] =
    self match {
      case HTTP  => Option(HttpScheme.HTTP)
      case HTTPS => Option(HttpScheme.HTTPS)
      case _     => None
    }

  def toWebSocketScheme: Option[WebSocketScheme] =
    self match {
      case WS  => Option(WebSocketScheme.WS)
      case WSS => Option(WebSocketScheme.WSS)
      case _   => None
    }
}
object Scheme       {

  def decode(scheme: String): Option[Scheme] =
    scheme.toUpperCase match {
      case "HTTPS" => Option(HTTPS)
      case "HTTP"  => Option(HTTP)
      case "WS"    => Option(WS)
      case "WSS"   => Option(WSS)
      case _       => None
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
