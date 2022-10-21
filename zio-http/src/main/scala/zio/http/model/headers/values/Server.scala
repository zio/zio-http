package zio.http.model.headers.values

/**
 * Server header value.
 */
sealed trait Server

  object Server {
    
    /**
     * A server value with a name
     */
    final case class ServerValue(name: String) extends Server

    /**
     * No server name
     */
    object EmptyServerValue extends Server

    def fromServer(server: Server): String =
      server match {
        case ServerValue(name) => name
        case EmptyServerValue => ""
      }

    def toServer(value: String): Server = {
      val serverTrim = value.trim
      if(serverTrim.isEmpty) EmptyServerValue else ServerValue(serverTrim)
    }
  }


