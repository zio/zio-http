package zio.http.model.headers.values

import scala.util.Try

/** Host header value. */
sealed trait Host

object Host{

  /** The Host header value has a host name and an optional port. */
  final case class HostValue(name: String, port: Option[Int]) extends Host

  /** The Host header value is invalid */
  case object InvalidHostValue extends Host

  def fromHost(host: Host): String = host match {
    case HostValue(name, port) => port.fold(name)(port => s"$name:$port")
    case InvalidHostValue => ""
  }

  def toHost(value: String): Host = {
    val valueTrimmed = value.trim
    val portSeparatorIndex = valueTrimmed.indexOf(":")
    (portSeparatorIndex match {
      case -1 if valueTrimmed.nonEmpty =>
      Try(HostValue(valueTrimmed, None))
      case 0 => Try(InvalidHostValue)
      case i if i < valueTrimmed.length -1  =>
        val (host, portWithColon) = valueTrimmed.splitAt(portSeparatorIndex)
        Try{
          portWithColon.tail.toInt
        }.map(p => HostValue(host, Some(p)))
      case _ => Try(InvalidHostValue)
    }).fold(_ => InvalidHostValue, identity)
  }
}


