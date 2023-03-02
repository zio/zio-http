package zio.http.model

import zio.Unsafe
import zio.stacktracer.TracingImplicits.disableAutoTrace

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
