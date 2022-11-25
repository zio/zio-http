package zio.http.model.headers.values

import zio.Chunk

sealed trait SecWebSocketExtensions

/**
 * The Sec-WebSocket-Extensions header is used in the WebSocket handshake. It is
 * initially sent from the client to the server, and then subsequently sent from
 * the server to the client, to agree on a set of protocol-level extensions to
 * use during the connection.
 *
 * See:
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Sec-WebSocket-Extensions
 */
object SecWebSocketExtensions {
  // Sec-WebSocket-Extensions: foo, bar; baz=2

  // Sec-WebSocket-Extensions: deflate-stream
  //         Sec-WebSocket-Extensions: mux; max-channels=4; flow-control,
  //          deflate-stream
  //         Sec-WebSocket-Extensions: private-extension

  // Sec-WebSocket-Extensions = extension-list
  //         extension-list = 1#extension
  //         extension = extension-token *( ";" extension-param )
  //         extension-token = registered-token
  //         registered-token = token
  //         extension-param = token [ "=" (token | quoted-string) ]
  //             ;When using the quoted-string syntax variant, the value
  //             ;after quoted-string unescaping MUST conform to the
  //             ;'token' ABNF.

  sealed trait Extension
  object Extension {
    final case class Parameter(name: String, value: String) extends Extension
    final case class TokenParam(name: String)               extends Extension
  }

  final case class Token(extension: Chunk[Extension])   extends SecWebSocketExtensions
  final case class Extensions(extensions: Chunk[Token]) extends SecWebSocketExtensions
  case object InvalidExtensions                         extends SecWebSocketExtensions

  def toSecWebSocketExtensions(value: String): SecWebSocketExtensions =
    if (value.trim().isEmpty) InvalidExtensions
    else {
      val extensions: Array[Token] = value
        .split(",")
        .map(_.trim)
        .flatMap { extension =>
          val parts  = extension.split(";").map(_.trim)
          val tokens =
            if (parts.length == 1) Array[Extension](Extension.TokenParam(parts(0)))
            else {
              val params: Array[Extension] = parts.map { part =>
                val value = part.split("=")
                val name  = value(0)
                if (value.length == 1) Extension.TokenParam(name)
                else Extension.Parameter(name, value(1))
              }
              params
            }
          Array(Token(Chunk.fromArray(tokens)))
        }
      Extensions(Chunk.fromArray(extensions))
    }

  def fromSecWebSocketExtensions(secWebSocketExtensions: SecWebSocketExtensions): String =
    secWebSocketExtensions match {
      case Extensions(extensions)              =>
        extensions
          .map(_.extension)
          .map(extension => renderParams(extension))
          .mkString(", ")
      case Token(extensions: Chunk[Extension]) => renderParams(extensions)
      case InvalidExtensions                   => ""
    }

  private def renderParams(extensions: Chunk[Extension]): String = {
    extensions.map {
      case Extension.TokenParam(value)      => value
      case Extension.Parameter(name, value) => s"$name=$value"
    }.mkString("; ")
  }

}
