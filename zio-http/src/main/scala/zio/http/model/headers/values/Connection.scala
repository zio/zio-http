package zio.http.model.headers.values

/**
 * Connection header value.
 */
sealed trait Connection {
  val value: String
}

object Connection {

  /**
   * This directive indicates that either the client or the server would like to
   * close the connection. This is the default on HTTP/1.0 requests.
   */
  case object Close extends Connection {
    override val value: String = "close"
  }

  /**
   * Any comma-separated list of HTTP headers [Usually keep-alive only]
   * indicates that the client would like to keep the connection open. Keeping a
   * connection open is the default on HTTP/1.1 requests. The list of headers
   * are the name of the header to be removed by the first non-transparent proxy
   * or cache in-between: these headers define the connection between the
   * emitter and the first entity, not the destination node.
   */
  case object KeepAlive extends Connection {
    override val value: String = "keep-alive"
  }

  /**
   * Any string other than "close" and "keep-alive" will be treated as
   * InvalidConnection.
   */
  case object InvalidConnection extends Connection {
    override val value: String = ""
  }

  def fromConnection(connection: Connection): String = connection.value

  def toConnection(connection: String): Connection = {
    connection.trim.toLowerCase() match {
      case Close.value     => Close
      case KeepAlive.value => KeepAlive
      case _               => InvalidConnection
    }
  }
}
