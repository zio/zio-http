package zhttp.internal

import io.netty.buffer.Unpooled
import zhttp.http.Scheme.{HTTP, HTTPS, WS, WSS}
import zhttp.http.URL.Location
import zhttp.http._
import zio.random.Random
import zio.stream.ZStream
import zio.test.{Gen, Sized}
import zio.{Chunk, ZIO}

import java.io.File

object HttpGen {
  def clientParamsForFileHttpData(): Gen[Random with Sized, Request] = {
    for {
      file    <- Gen.fromEffect(ZIO.succeed(new File(getClass.getResource("/TestFile.txt").getPath)))
      method  <- HttpGen.method
      url     <- HttpGen.url
      headers <- Gen.listOf(HttpGen.header).map(Headers(_))
      version <- httpVersion
    } yield Request(version, method, url, headers, data = HttpData.fromFile(file))
  }

  def requestGen[R](
    dataGen: Gen[R, HttpData],
    methodGen: Gen[R, Method] = HttpGen.method,
    urlGen: Gen[Random with Sized, URL] = HttpGen.url,
    headerGen: Gen[Random with Sized, Header] = HttpGen.header,
  ): Gen[R with Random with Sized, Request] =
    for {
      method  <- methodGen
      url     <- urlGen
      headers <- Gen.listOf(headerGen).map(Headers(_))
      data    <- dataGen
      version <- httpVersion
    } yield Request(version, method, url, headers, data = data)

  def httpVersion: Gen[Random with Sized, Version] =
    Gen.fromIterable(List(Version.Http_1_0, Version.Http_1_1))

  def cookies: Gen[Random with Sized, Cookie] = for {
    name     <- Gen.anyString
    content  <- Gen.anyString
    expires  <- Gen.option(Gen.anyInstant)
    domain   <- Gen.option(Gen.anyString)
    path     <- Gen.option(path)
    secure   <- Gen.boolean
    httpOnly <- Gen.boolean
    maxAge   <- Gen.option(Gen.anyLong)
    sameSite <- Gen.option(Gen.fromIterable(List(Cookie.SameSite.Strict, Cookie.SameSite.Lax)))
    secret   <- Gen.option(Gen.anyString)
  } yield Cookie(name, content, expires, domain, path, secure, httpOnly, maxAge, sameSite, secret)

  def genAbsoluteLocation: Gen[Random with Sized, Location.Absolute] = for {
    scheme <- Gen.fromIterable(List(Scheme.HTTP, Scheme.HTTPS))
    host   <- Gen.alphaNumericStringBounded(1, 5)
    port   <- Gen.int(0, Int.MaxValue)
  } yield URL.Location.Absolute(scheme, host, port)

  def genAbsoluteURL = for {
    path        <- HttpGen.path
    kind        <- HttpGen.genAbsoluteLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)

  def genRelativeLocation: Gen[Any, Location.Relative.type] = Gen.const(URL.Location.Relative)

  def header: Gen[Random with Sized, Header] = for {
    key   <- Gen.alphaNumericStringBounded(1, 4)
    value <- Gen.alphaNumericStringBounded(1, 4)
  } yield (key, value)

  def httpData[R](gen: Gen[R, List[String]]): Gen[R, HttpData] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            HttpData.fromStream(ZStream.fromIterable(list).map(b => Chunk.fromArray(b.getBytes())).flattenChunks),
            HttpData.fromString(list.mkString("")),
            HttpData.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            HttpData.fromByteBuf(Unpooled.copiedBuffer(list.mkString(""), HTTP_CHARSET)),
            HttpData.empty,
          ),
        )
    } yield cnt

  def location: Gen[Random with Sized, URL.Location] = {
    Gen.fromIterable(List(genRelativeLocation, genAbsoluteLocation)).flatten
  }

  def method: Gen[Any, Method] = Gen.fromIterable(
    List(
      Method.OPTIONS,
      Method.GET,
      Method.HEAD,
      Method.POST,
      Method.PUT,
      Method.PATCH,
      Method.DELETE,
      Method.TRACE,
      Method.CONNECT,
    ),
  )

  def nonEmptyHttpData[R](gen: Gen[R, List[String]]): Gen[R, HttpData] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            HttpData.fromStream(ZStream.fromIterable(list).map(b => Chunk.fromArray(b.getBytes())).flattenChunks),
            HttpData.fromString(list.mkString("")),
            HttpData.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            HttpData.fromByteBuf(Unpooled.copiedBuffer(list.mkString(""), HTTP_CHARSET)),
          ),
        )
    } yield cnt

  def path: Gen[Random with Sized, Path] = for {
    segments      <- Gen.listOf(Gen.alphaNumericStringBounded(1, 5))
    trailingSlash <- Gen.boolean
  } yield Path(segments.toVector, trailingSlash)

  def request: Gen[Random with Sized, Request] = for {
    version <- httpVersion
    method  <- HttpGen.method
    url     <- HttpGen.url
    headers <- Gen.listOf(HttpGen.header).map(Headers(_))
    data    <- HttpGen.httpData(Gen.listOf(Gen.alphaNumericString))
  } yield Request(version, method, url, headers, data)

  def response[R](gContent: Gen[R, List[String]]): Gen[Random with Sized with R, Response] = {
    for {
      content <- HttpGen.httpData(gContent)
      headers <- HttpGen.header.map(Headers(_))
      status  <- HttpGen.status
    } yield Response(status, headers, content)
  }

  def scheme: Gen[Any, Scheme] = Gen.fromIterable(List(HTTP, HTTPS, WS, WSS))

  def status: Gen[Any, Status] = Gen.fromIterable(
    List(
      Status.Continue,
      Status.SwitchingProtocols,
      Status.Processing,
      Status.Ok,
      Status.Created,
      Status.Accepted,
      Status.NonAuthoritiveInformation,
      Status.NoContent,
      Status.ResetContent,
      Status.PartialContent,
      Status.MultiStatus,
      Status.MultipleChoices,
      Status.MovedPermanently,
      Status.Found,
      Status.SeeOther,
      Status.NotModified,
      Status.UseProxy,
      Status.TemporaryRedirect,
      Status.PermanentRedirect,
      Status.BadRequest,
      Status.Unauthorized,
      Status.PaymentRequired,
      Status.Forbidden,
      Status.NotFound,
      Status.MethodNotAllowed,
      Status.NotAcceptable,
      Status.ProxyAuthenticationRequired,
      Status.RequestTimeout,
      Status.Conflict,
      Status.Gone,
      Status.LengthRequired,
      Status.PreconditionFailed,
      Status.RequestEntityTooLarge,
      Status.RequestUriTooLong,
      Status.UnsupportedMediaType,
      Status.RequestedRangeNotSatisfiable,
      Status.ExpectationFailed,
      Status.MisdirectedRequest,
      Status.UnprocessableEntity,
      Status.Locked,
      Status.FailedDependency,
      Status.UnorderedCollection,
      Status.UpgradeRequired,
      Status.PreconditionRequired,
      Status.TooManyRequests,
      Status.RequestHeaderFieldsTooLarge,
      Status.InternalServerError,
      Status.NotImplemented,
      Status.BadGateway,
      Status.ServiceUnavailable,
      Status.GatewayTimeout,
      Status.HttpVersionNotSupported,
      Status.VariantAlsoNegotiates,
      Status.InsufficientStorage,
      Status.NotExtended,
      Status.NetworkAuthenticationRequired,
    ),
  )

  def url: Gen[Random with Sized, URL] = for {
    path        <- HttpGen.path
    kind        <- HttpGen.location
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)

}
