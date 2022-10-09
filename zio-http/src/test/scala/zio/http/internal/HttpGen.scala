package zio.http.internal

import io.netty.buffer.Unpooled
import zio._
import zio.http.Path.Segment
import zio.http.URL.Location
import zio.http._
import zio.http.model._
import zio.http.model.headers.values.{Allow, CacheControl}
import zio.stream.ZStream
import zio.test.{Gen, Sized}

import java.io.File

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
    } yield Request(Body.fromFile(file), headers, method, url, version, None)
  }

  def genAbsoluteLocation: Gen[Sized, Location.Absolute] = for {
    scheme <- Gen.fromIterable(List(Scheme.HTTP, Scheme.HTTPS))
    host   <- Gen.alphaNumericStringBounded(1, 5)
    port   <- Gen.int(0, 65536)
  } yield URL.Location.Absolute(scheme, host, port)

  def genRelativeURL = for {
    path        <- HttpGen.anyPath
    kind        <- HttpGen.genRelativeLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.chunkOf(Gen.alphaNumericString))
  } yield URL(path, kind, QueryParams(queryParams))

  def genAbsoluteURL = for {
    path        <- HttpGen.nonEmptyPath
    kind        <- HttpGen.genAbsoluteLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.chunkOf(Gen.alphaNumericString))
  } yield URL(path, kind, QueryParams(queryParams))

  def genRelativeLocation: Gen[Any, Location.Relative.type] = Gen.const(URL.Location.Relative)

  def header: Gen[Sized, Header] = for {
    key   <- Gen.alphaNumericStringBounded(1, 4)
    value <- Gen.alphaNumericStringBounded(1, 4)
  } yield Header(key, value)

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
  } yield Request(data, headers, method, url, version, None)

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
    } yield Request(data, headers, method, url, version, None)

  def response[R](gContent: Gen[R, List[String]]): Gen[Sized with R, Response] = {
    for {
      content <- HttpGen.body(gContent)
      headers <- HttpGen.header
      status  <- HttpGen.status
    } yield Response(status, headers, content)
  }

  def scheme: Gen[Any, Scheme] = Gen.fromIterable(
    List(Scheme.HTTP, Scheme.HTTPS, Scheme.WS, Scheme.WSS),
  )

  def status: Gen[Any, Status] = Gen.fromIterable(
    List(
      Status.Continue,
      Status.SwitchingProtocols,
      Status.Processing,
      Status.Ok,
      Status.Created,
      Status.Accepted,
      Status.NonAuthoritativeInformation,
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
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.chunkOf(Gen.alphaNumericString))
  } yield URL(path, kind, QueryParams(queryParams))

  def cacheControlSingleValue(seconds: Int): Gen[Any, CacheControl] =
    Gen.fromIterable(
      List(
        CacheControl.Immutable,
        CacheControl.InvalidCacheControl,
        CacheControl.MaxAge(seconds),
        CacheControl.MaxStale(seconds),
        CacheControl.MinFresh(seconds),
        CacheControl.MustRevalidate,
        CacheControl.MustUnderstand,
        CacheControl.NoCache,
        CacheControl.NoStore,
        CacheControl.NoTransform,
        CacheControl.OnlyIfCached,
        CacheControl.Private,
        CacheControl.ProxyRevalidate,
        CacheControl.Public,
        CacheControl.SMaxAge(seconds),
        CacheControl.StaleIfError(seconds),
        CacheControl.StaleWhileRevalidate(seconds),
      ),
    )

  def cacheControlSingleValueWithSeconds: Gen[Any, CacheControl] = for {
    duration <- Gen.int(0, 1000000)
    value    <- cacheControlSingleValue(duration)
  } yield value

  def cacheControl: Gen[Any, CacheControl] = {
    Gen.chunkOfBounded(1, 10)(cacheControlSingleValueWithSeconds).map(CacheControl.MultipleCacheControlValues.apply)
  }

  def allowHeaderSingleValue: Gen[Any, Allow] = Gen.fromIterable(
    List(
      Allow.OPTIONS,
      Allow.GET,
      Allow.HEAD,
      Allow.POST,
      Allow.PUT,
      Allow.PATCH,
      Allow.DELETE,
      Allow.TRACE,
      Allow.CONNECT,
    ),
  )

  def allowHeader: Gen[Any, Allow] =
    Gen.chunkOfBounded(1, 9)(allowHeaderSingleValue).map(Allow.AllowMethods.apply)
}
