package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.socket.SocketApp

import java.time.Instant

// RESPONSE
sealed trait Response[-R, +E] extends Product with Serializable { self => }

object Response extends ResponseHelpers {
  // Constructors
  final case class HttpResponse[-R, +E](status: Status, headers: List[Header], content: HttpData[R, E])
      extends Response[R, E]
      with HasHeaders
      with HeadersHelpers

  final case class SocketResponse[-R, +E](socket: SocketApp[R, E] = SocketApp.empty) extends Response[R, E]

  /**
   * returns a list of cookies from response header
   */
  def cookies(header: List[Header]): List[Cookie] = {
    header
      .filter(x => x.name.toString.equalsIgnoreCase(HttpHeaderNames.SET_COOKIE.toString))
      .map(h =>
        Cookie.fromString(h.value.toString) match {
          case Left(value)  => throw value
          case Right(value) => value
        },
      )
  }

  /**
   * response with SET_COOKIE header to add cookies
   */
  def addCookie(cookie: Cookie): UResponse =
    http(headers = List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.asString)))

  /**
   * response with SET_COOKIE header to remove cookies
   */
  def removeCookie(cookie: Cookie): UResponse =
    http(headers = List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.clearCookie.asString)))

  def removeCookie(cookie: String): UResponse = http(headers =
    List(
      Header.custom(
        HttpHeaderNames.SET_COOKIE.toString,
        Cookie(cookie, content = "", expires = Some(Instant.ofEpochSecond(0))).asString,
      ),
    ),
  )
}
