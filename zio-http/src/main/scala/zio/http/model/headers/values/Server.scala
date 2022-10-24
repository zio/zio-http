package zio.http.model.headers.values

/**
 * Server header value.
 */
sealed trait Server

object Server {

  /**
   * A server value with a name
   */
  final case class ServerName(name: String) extends Server

  /**
   * No server name
   */
  object EmptyServerName extends Server

  def fromServer(server: Server): String =
    server match {
      case ServerName(name) => name
      case EmptyServerName  => ""
    }

  def toServer(value: String): Server = {
    val serverTrim = value.trim
    if (serverTrim.isEmpty) EmptyServerName else ServerName(serverTrim)
  }
}
