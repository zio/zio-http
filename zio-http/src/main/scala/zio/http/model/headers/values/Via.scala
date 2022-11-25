package zio.http.model.headers.values

import zio.Chunk

sealed trait Via

/**
 * The Via general header is added by proxies, both forward and reverse, and can
 * appear in the request or response headers. It is used for tracking message
 * forwards, avoiding request loops, and identifying the protocol capabilities
 * of senders along the request/response chain
 */
object Via {
  // Via: 1.1 vegur
  // Via: HTTP/1.1 GWA
  // Via: 1.0 fred, 1.1 p.example.net
  final case class ViaValues(values: Chunk[Via]) extends Via
  final case class ViaValue(protocol: String, host: String, port: Option[Int] = None, comment: Option[String] = None)
      extends Via
  final case class InvalidVia(value: String)     extends Via

  def toVia(values: String): Via = if (values.isEmpty) InvalidVia(values)
  else {
    val parsedValues: Array[Via] = values
      .split(",")
      .map(_.trim)
      .map { value =>
        val parts = value.split(" ")
        if (parts.length == 2) {
          val protocol = parts(0)
          val host     = parts(1)
          ViaValue(protocol, host, None, None)
        } else if (parts.length == 3) {
          val protocol = parts(0)
          val host     = parts(1)
          val port     = parts(2).toInt
          ViaValue(protocol, host, Some(port), None)
        } else if (parts.length == 4) {
          val protocol = parts(0)
          val host     = parts(1)
          val port     = parts(2).toInt
          val comment  = parts(3)
          ViaValue(protocol, host, Some(port), Some(comment))
        } else {
          InvalidVia(value)
        }
      }

    ViaValues(Chunk.fromArray(parsedValues))
  }

  def fromVia(via: Via): String = via match {
    case ViaValues(values)                       =>
      values.map {
        case ViaValue(protocol, host, port, comment) =>
          formatViaValue(protocol, host, port, comment)
        case InvalidVia(value)                       => value
      }.mkString(", ")
    case ViaValue(protocol, host, port, comment) =>
      formatViaValue(protocol, host, port, comment)
    case InvalidVia(value)                       => value
  }

  private def formatViaValue(protocol: String, host: String, port: Option[Int], comment: Option[String]): String = {
    val portString    = port.fold("")(p => s" $p")
    val commentString = comment.fold("")(c => s" $c")
    s"$protocol $host$portString$commentString"
  }
}
