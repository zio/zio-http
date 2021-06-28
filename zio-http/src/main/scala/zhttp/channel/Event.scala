package zhttp.channel
import io.netty.buffer.{ByteBuf => JByteBuf}
import io.netty.handler.codec.http.{
  HttpContent => JHttpContent,
  HttpRequest => JHttpRequest,
  LastHttpContent => JLastHttpContent,
}
import zhttp.http.{Header, Method, URL}

/**
 * Represents all the incoming events on a channel
 */
sealed trait Event[+A]
object Event {
  case class Request(method: Method, url: URL, headers: List[Header]) extends Event[Nothing]
  case class Content[A](data: A)                                      extends Event[A]
  case class End[A](data: A)                                          extends Event[A]
  case class Failure(cause: Throwable)                                extends Event[Nothing]
  case object Complete                                                extends Event[Nothing]

  private[zhttp] def decode(msg: Any): Event[JByteBuf] =
    msg match {
      case msg: JHttpRequest =>
        val uri = URL.fromString(msg.uri()) match {
          case Right(uri) => uri
          case _          => null
        }

        Event.Request(
          Method.fromJHttpMethod(msg.method()),
          uri,
          Header.make(msg.headers()),
        )

      case msg: JLastHttpContent =>
        Event.End(msg.content())

      case msg: JHttpContent =>
        Event.Content(msg.content())

      case msg => throw new Error(s"Invalid channel event: ${msg}")
    }
}
