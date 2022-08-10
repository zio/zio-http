package zhttp.internal

import io.netty.buffer.Unpooled
import zhttp.http.Path.Segment
import zhttp.http.Scheme.{HTTP, HTTPS, WS, WSS}
import zhttp.http.URL.Location
import zhttp.http._
import zio.stream.ZStream
import zio.test.{Gen, Sized}
import zio.{Chunk, ZIO}

import java.io.File
import java.time.Instant

object HttpGen {
  def anyPath: Gen[Sized, Path] = for {
    segments <- Gen.listOfBounded(0, 5)(
      Gen.oneOf(
        Gen.alphaNumericStringBounded(0, 5).map(Segment(_)),
        Gen.elements(Segment.root),
      ),
    )
  } yield Path(segments.toVector)

  def clientParamsForFileBody(): Gen[Sized, Request] = {
    for {
      file    <- Gen.fromZIO(ZIO.succeed(new File(getClass.getResource("/TestFile.txt").getPath)))
      method  <- HttpGen.method
      url     <- HttpGen.url
      headers <- Gen.listOf(HttpGen.header).map(Headers(_))
      version <- httpVersion
    } yield Request(version, method, url, headers, body = Body.fromFile(file))
  }

  def cookies: Gen[Sized, Cookie] = for {
    name     <- Gen.string
    content  <- Gen.string
    expires  <- Gen.option(
      Gen.instant(Instant.parse("0001-01-01T00:00:00.00Z"), Instant.parse("9999-12-31T23:59:00.00Z")),
    )
    domain   <- Gen.option(Gen.string)
    path     <- Gen.option(HttpGen.anyPath)
    secure   <- Gen.boolean
    httpOnly <- Gen.boolean
    maxAge   <- Gen.option(Gen.long)
    sameSite <- Gen.option(Gen.fromIterable(List(Cookie.SameSite.Strict, Cookie.SameSite.Lax)))
    secret   <- Gen.option(Gen.string)
  } yield Cookie(name, content, expires, domain, path, secure, httpOnly, maxAge, sameSite, secret)

  def genAbsoluteLocation: Gen[Sized, Location.Absolute] = for {
    scheme <- Gen.fromIterable(List(Scheme.HTTP, Scheme.HTTPS))
    host   <- Gen.alphaNumericStringBounded(1, 5)
    port   <- Gen.int(0, 65536)
  } yield URL.Location.Absolute(scheme, host, port)

  def genRelativeURL = for {
    path        <- HttpGen.anyPath
    kind        <- HttpGen.genRelativeLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)

  def genAbsoluteURL = for {
    path        <- HttpGen.nonEmptyPath
    kind        <- HttpGen.genAbsoluteLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)

  def genRelativeLocation: Gen[Any, Location.Relative.type] = Gen.const(URL.Location.Relative)

  def header: Gen[Sized, Header] = for {
    key   <- Gen.alphaNumericStringBounded(1, 4)
    value <- Gen.alphaNumericStringBounded(1, 4)
  } yield (key, value)

  def body[R](gen: Gen[R, List[String]]): Gen[R, Body] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            Body.fromStream(ZStream.fromIterable(list).map(b => Chunk.fromArray(b.getBytes())).flattenChunks),
            Body.fromString(list.mkString("")),
            Body.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            Body.fromByteBuf(Unpooled.copiedBuffer(list.mkString(""), HTTP_CHARSET)),
            Body.empty,
          ),
        )
    } yield cnt

  def httpVersion: Gen[Sized, Version] =
    Gen.fromIterable(List(Version.Http_1_0, Version.Http_1_1))

  def location: Gen[Sized, URL.Location] = {
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

  def nonEmptyBody[R](gen: Gen[R, List[String]]): Gen[R, Body] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            Body.fromStream(ZStream.fromIterable(list).map(b => Chunk.fromArray(b.getBytes())).flattenChunks),
            Body.fromString(list.mkString("")),
            Body.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            Body.fromByteBuf(Unpooled.copiedBuffer(list.mkString(""), HTTP_CHARSET)),
          ),
        )
    } yield cnt

  def nonEmptyPath: Gen[Sized, Path] = for {
    segments <-
      Gen.listOfBounded(1, 5)(
        Gen.oneOf(
          Gen.alphaNumericStringBounded(0, 5).map(Segment(_)),
          Gen.elements(Segment.root),
        ),
      )

  } yield Path(segments.toVector)

  def request: Gen[Sized, Request] = for {
    version <- httpVersion
    method  <- HttpGen.method
    url     <- HttpGen.url
    headers <- Gen.listOf(HttpGen.header).map(Headers(_))
    data    <- HttpGen.body(Gen.listOf(Gen.alphaNumericString))
  } yield Request(version, method, url, headers, data)

  def requestGen[R](
    dataGen: Gen[R, Body],
    methodGen: Gen[R, Method] = HttpGen.method,
    urlGen: Gen[Sized, URL] = HttpGen.url,
    headerGen: Gen[Sized, Header] = HttpGen.header,
  ): Gen[R with Sized, Request] =
    for {
      method  <- methodGen
      url     <- urlGen
      headers <- Gen.listOf(headerGen).map(Headers(_))
      data    <- dataGen
      version <- httpVersion
    } yield Request(version, method, url, headers, body = data)

  def response[R](gContent: Gen[R, List[String]]): Gen[Sized with R, Response] = {
    for {
      content <- HttpGen.body(gContent)
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

  def url: Gen[Sized, URL] = for {
    path        <- Gen.elements(Path.root, Path.root / "a", Path.root / "a" / "b", Path.root / "a" / "b" / "c")
    kind        <- HttpGen.location
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)

}
