package zio.http.model

import io.netty.handler.codec.http.HttpScheme
import io.netty.handler.codec.http.websocketx.WebSocketScheme
import zio.Unsafe
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

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

  def port: Int = self match {
    case Scheme.HTTP  => 80
    case Scheme.HTTPS => 443
    case Scheme.WS    => 80
    case Scheme.WSS   => 443
  }

  def toJHttpScheme: Option[HttpScheme] = self match {
    case Scheme.HTTP  => Option(HttpScheme.HTTP)
    case Scheme.HTTPS => Option(HttpScheme.HTTPS)
    case _            => None
  }

  def toJWebSocketScheme: Option[WebSocketScheme] = self match {
    case Scheme.WS  => Option(WebSocketScheme.WS)
    case Scheme.WSS => Option(WebSocketScheme.WSS)
    case _          => None
  }
}
object Scheme       {

  /**
   * Decodes a string to an Option of Scheme. Returns None in case of
   * null/non-valid Scheme
   */
  def decode(scheme: String): Option[Scheme] =
    Option(unsafe.decode(scheme)(Unsafe.unsafe))

  def fromJScheme(scheme: HttpScheme): Option[Scheme] = scheme match {
    case HttpScheme.HTTPS => Option(Scheme.HTTPS)
    case HttpScheme.HTTP  => Option(Scheme.HTTP)
    case _                => None
  }

  def fromJScheme(scheme: WebSocketScheme): Option[Scheme] = scheme match {
    case WebSocketScheme.WSS => Option(Scheme.WSS)
    case WebSocketScheme.WS  => Option(Scheme.WS)
    case _                   => None
  }

  private[zio] object unsafe {
    def decode(scheme: String)(implicit unsafe: Unsafe): Scheme = {
      if (scheme == null) null
      else
        scheme.length match {
          case 5 => Scheme.HTTPS
          case 4 => Scheme.HTTP
          case 3 => Scheme.WSS
          case 2 => Scheme.WS
          case _ => null
        }
    }
  }

  case object HTTP extends Scheme

  case object HTTPS extends Scheme

  case object WS extends Scheme

  case object WSS extends Scheme
}
