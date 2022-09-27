package zio.http.model.headers

import zio.ZIO

import scala.util.Try

object HeaderValue {

  /**
   * Host header value
   */
  sealed trait Host
  object Host {
    final case class HostValue(hostAddress: String, port: Option[Int] = None) extends Host
    object HostValue {
      def apply(hostAddress: String, port: Int): HostValue = HostValue(hostAddress, Some(port))
    }
    case object EmptyHostValue extends Host
    case object InvalidHostValue extends Host

    private def parse(value: String): Host = {
      value.split(":").toList match {
        case host :: portS :: Nil => Try(portS.toInt).fold(_ => InvalidHostValue, port => HostValue(host, Some(port)))
        case host :: Nil          => HostValue(host)
        case _                    => InvalidHostValue
      }
    }

    def fromHost(host: Host): String =
      host match {
        case HostValue(address, None)          => address
        case HostValue(address, Some(port))    => s"$address:$port"
        case EmptyHostValue | InvalidHostValue => ""
      }

    def toHost(value: String): Host =
      if (value.isEmpty) EmptyHostValue else parse(value)
  }
}
