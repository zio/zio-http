package zhttp.http

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http.HttpError.HTTPErrorWithCause
import zhttp.socket.{Socket, SocketApp, WebSocketFrame}
import zio.Chunk
import zhttp.http.HttpData.Empty
import zhttp.http.HttpData.Text
import zhttp.http.HttpData.Binary
import zhttp.http.HttpData.BinaryN
import zhttp.http.HttpData.BinaryStream

import java.io.{PrintWriter, StringWriter}
import scala.collection.immutable

case class Response[-R, +E] private (
  status: Status,
  headers: List[Header],
  data: HttpData[R, E],
  private[zhttp] val attribute: HttpAttribute[R, E],
) extends HeaderExtension[Response[R, E]] { self =>

  /**
   * Sets the status of the response
   */
  def setStatus(status: Status): Response[R, E] =
    self.copy(status = status)

  /**
   * Adds cookies in the response headers
   */
  def addCookie(cookie: Cookie): Response[R, E] =
    self.copy(headers = self.headers ++ List(Header.custom(HttpHeaderNames.SET_COOKIE.toString, cookie.encode)))

    def defaultHealders: Response[R,E] = self.headers.map(_.name.toString.toLowerCase()).filter(_=="content-length") match {
      case _ :: _ => self
  
      case immutable.Nil =>self.data match {
          case Empty => copy(headers=self.headers ++ List(Header("Content-Length",{0}.toString())))
          case Text(text, _) =>copy(headers=self.headers ++ List(Header("Content-Length",{text.getBytes().length}.toString())))
          case Binary(data) =>copy(headers=self.headers ++ List(Header("Content-Length",{data.toVector.length}.toString())))
          case BinaryN(data) =>copy(headers=self.headers ++ List(Header("Content-Length",{data.array().length}.toString())))
          case BinaryStream(_) =>copy(headers=self.headers ++ List(Header("Transer-Encoding","chunked")))
        }
     
    }  
        
  /**
   * Removes headers by name from the response
   */
  override def removeHeaders(headers: List[String]): Response[R, E] =
    self.copy(headers = self.headers.filterNot(h => headers.contains(h.name)))

  /**
   * Adds headers to response
   */
  override def addHeaders(headers: List[Header]): Response[R, E] =
    self.copy(headers = self.headers ++ headers)

  /**
   * Gets cookies from the response headers
   */
  def cookies: List[Cookie] = getCookieFromHeader(HttpHeaderNames.SET_COOKIE)

  def getContentLength: Option[Long] = self.data.size

  /**
   * Automatically detects the size of the content and sets it
   */
  def setPayloadHeaders: Response[R, E] = {
    getContentLength match {
      case Some(value) => setContentLength(value)
      case None        => setChunkedEncoding
    }
  }
}

object Response {
  def apply[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.Empty,
  ): Response[R, E] =
    Response(status, headers, data, HttpAttribute.empty)

  @deprecated("Use `Response(status, headers, data)` constructor instead.", "22-Sep-2021")
  def http[R, E](
    status: Status = Status.OK,
    headers: List[Header] = Nil,
    data: HttpData[R, E] = HttpData.empty,
  ): Response[R, E] = Response(status, headers, data)

  /**
   * Creates a socket response using an app
   */
  def socket[R, E](ss: SocketApp[R, E]): Response[R, E] =
    Response(Status.SWITCHING_PROTOCOLS, Nil, HttpData.empty, HttpAttribute.socket(ss))

  /**
   * Creates a new WebSocket Response
   */
  def socket[R, E](ss: Socket[R, E, WebSocketFrame, WebSocketFrame]): Response[R, E] =
    SocketApp.message(ss).asResponse

  def fromHttpError(error: HttpError): UResponse = {
    error match {
      case cause: HTTPErrorWithCause =>
        Response(
          error.status,
          Nil,
          HttpData.fromChunk(cause.cause match {
            case Some(throwable) =>
              val sw = new StringWriter
              throwable.printStackTrace(new PrintWriter(sw))
              Chunk.fromArray(s"${cause.message}:\n${sw.toString}".getBytes(HTTP_CHARSET))
            case None            => Chunk.fromArray(s"${cause.message}".getBytes(HTTP_CHARSET))
          }),
        )
      case _ => Response(error.status, Nil, HttpData.fromChunk(Chunk.fromArray(error.message.getBytes(HTTP_CHARSET))))
    }
  }

  def ok: UResponse = Response(Status.OK)

  def text(text: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(text.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeTextPlain),
    )

  def jsonString(data: String): UResponse =
    Response(
      data = HttpData.fromChunk(Chunk.fromArray(data.getBytes(HTTP_CHARSET))),
      headers = List(Header.contentTypeJson),
    )

  def status(status: Status): UResponse = Response(status)

  def temporaryRedirect(location: String): Response[Any, Nothing] =
    Response(Status.TEMPORARY_REDIRECT, List(Header.location(location)))

  def permanentRedirect(location: String): Response[Any, Nothing] =
    Response(Status.PERMANENT_REDIRECT, List(Header.location(location)))
}
