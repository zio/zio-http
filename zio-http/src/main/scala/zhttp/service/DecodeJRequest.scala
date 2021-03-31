package zhttp.service

import io.netty.handler.codec.http.multipart.{Attribute, FileUpload, HttpData => JHttpData, HttpPostRequestDecoder}
import io.netty.handler.codec.http.{HttpHeaders, HttpMethod}
import zhttp.core.JFullHttpRequest
import zhttp.http.HttpData.{AttributeData, FileData, MultipartFormData}
import zhttp.http._
import zio.blocking.Blocking
import zio.stream.ZStream

import scala.util.{Success, Try}

trait DecodeJRequest {

  /**
   * Tries to decode the [io.netty.handler.codec.http.FullHttpRequest] to [Request].
   */
  def decodeJRequest(jReq: JFullHttpRequest): Either[HttpError, Request] = for {
    url <- URL.fromString(jReq.uri())
    method   = Method.fromJHttpMethod(jReq.method())
    headers  = Header.make(jReq.headers())
    endpoint = method -> url
    data     = HttpData.fromByteBuf(jReq.content())
  } yield Request(endpoint, headers, data)

  def decodeMultipart(
    url: String,
    reqHeaders: HttpHeaders,
    decoder: HttpPostRequestDecoder,
  ): Either[Throwable, Request] = for {
    url <- URL.fromString(url)
    method   = Method.fromJHttpMethod(HttpMethod.POST)
    headers  = Header.make(reqHeaders)
    endpoint = method -> url
    bodies <-
      decoder
        .getBodyHttpDatas()
        .toArray()
        .foldLeft(Try(MultipartFormData.empty)) {
          case (Success(acc), f: FileUpload) =>
            httpDataToStream(f).map(data =>
              acc.copy(files = acc.files + (f.getName -> FileData(f.getName, f.getContentType, data))),
            )
          case (Success(acc), a: Attribute)  =>
            httpDataToStream(a).map(data =>
              acc.copy(attributes = acc.attributes + (a.getName -> AttributeData(a.getName, data))),
            )
          case (acc, _)                      =>
            acc
        }
        .toEither
  } yield Request(endpoint, headers, bodies)

  private def httpDataToStream(data: JHttpData): Try[ZStream[Blocking, Throwable, Byte]] = {
    // First try to get the underlying File (for larger uploads). If this doesn't exist,
    // get the in-memory data. Both calls may fail with an IOException
    Try(data.getFile.toPath).map(ZStream.fromFile(_)).orElse(Try(ZStream.fromIterable(data.get())))
  }
}
