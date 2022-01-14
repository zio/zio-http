package zhttp.internal

import io.netty.buffer.Unpooled
import zhttp.http._
import zhttp.service.Client.ClientParams
import zio._
import zio.stream.ZStream
import zio.test.{Gen, Sized}

import java.io.File

object HttpGen {
  def clientParams[R](dataGen: Gen[R, HttpData]) =
    for {
      method  <- HttpGen.method
      url     <- HttpGen.url
      headers <- Gen.listOf(HttpGen.header).map(Headers(_))
      data    <- dataGen
    } yield ClientParams(method, url, headers, data)

  def clientParamsForFileHttpData() = {
    for {
      file    <- Gen.fromZIO(ZIO.succeed(new File(getClass.getResource("/TestFile.txt").getPath)))
      method  <- HttpGen.method
      url     <- HttpGen.url
      headers <- Gen.listOf(HttpGen.header).map(Headers(_))
    } yield ClientParams(method, url, headers, HttpData.fromFile(file))
  }

  def cookies: Gen[Random with Sized, Cookie] = for {
    name     <- Gen.string
    content  <- Gen.string
    expires  <- Gen.option(Gen.instant)
    domain   <- Gen.option(Gen.string)
    path     <- Gen.option(path)
    secure   <- Gen.boolean
    httpOnly <- Gen.boolean
    maxAge   <- Gen.option(Gen.long)
    sameSite <- Gen.option(Gen.fromIterable(List(Cookie.SameSite.Strict, Cookie.SameSite.Lax)))
    secret   <- Gen.option(Gen.string)
  } yield Cookie(name, content, expires, domain, path, secure, httpOnly, maxAge, sameSite, secret)

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
    def genRelative = Gen.const(URL.Location.Relative)

    def genAbsolute = for {
      scheme <- Gen.fromIterable(List(Scheme.HTTP, Scheme.HTTPS))
      host   <- Gen.alphaNumericStringBounded(1, 5)
      port   <- Gen.int(0, Int.MaxValue)
    } yield URL.Location.Absolute(scheme, host, port)

    Gen.fromIterable(List(genRelative, genAbsolute)).flatten
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

  def path: Gen[Random with Sized, Path] = {
    for {
      l <- Gen.listOf(Gen.alphaNumericString)
      p <- Gen.const(Path(l))
    } yield p
  }

  def request: Gen[Random with Sized, Request] = for {
    method  <- HttpGen.method
    url     <- HttpGen.url
    headers <- Gen.listOf(HttpGen.header).map(Headers(_))
    data    <- HttpGen.httpData(Gen.listOf(Gen.alphaNumericString))
  } yield Request(method, url, headers, None, data)

  def response[R](gContent: Gen[R, List[String]]): Gen[Random with Sized with R, Response] = {
    for {
      content <- HttpGen.httpData(gContent)
      headers <- HttpGen.header.map(Headers(_))
      status  <- HttpGen.status
    } yield Response(status, headers, content)
  }

  def status: Gen[Any, Status] = Gen.fromIterable(
    List(
      Status.CONTINUE,
      Status.SWITCHING_PROTOCOLS,
      Status.PROCESSING,
      Status.OK,
      Status.CREATED,
      Status.ACCEPTED,
      Status.NON_AUTHORITATIVE_INFORMATION,
      Status.NO_CONTENT,
      Status.RESET_CONTENT,
      Status.PARTIAL_CONTENT,
      Status.MULTI_STATUS,
      Status.MULTIPLE_CHOICES,
      Status.MOVED_PERMANENTLY,
      Status.FOUND,
      Status.SEE_OTHER,
      Status.NOT_MODIFIED,
      Status.USE_PROXY,
      Status.TEMPORARY_REDIRECT,
      Status.PERMANENT_REDIRECT,
      Status.BAD_REQUEST,
      Status.UNAUTHORIZED,
      Status.PAYMENT_REQUIRED,
      Status.FORBIDDEN,
      Status.NOT_FOUND,
      Status.METHOD_NOT_ALLOWED,
      Status.NOT_ACCEPTABLE,
      Status.PROXY_AUTHENTICATION_REQUIRED,
      Status.REQUEST_TIMEOUT,
      Status.CONFLICT,
      Status.GONE,
      Status.LENGTH_REQUIRED,
      Status.PRECONDITION_FAILED,
      Status.REQUEST_ENTITY_TOO_LARGE,
      Status.REQUEST_URI_TOO_LONG,
      Status.UNSUPPORTED_MEDIA_TYPE,
      Status.REQUESTED_RANGE_NOT_SATISFIABLE,
      Status.EXPECTATION_FAILED,
      Status.MISDIRECTED_REQUEST,
      Status.UNPROCESSABLE_ENTITY,
      Status.LOCKED,
      Status.FAILED_DEPENDENCY,
      Status.UNORDERED_COLLECTION,
      Status.UPGRADE_REQUIRED,
      Status.PRECONDITION_REQUIRED,
      Status.TOO_MANY_REQUESTS,
      Status.REQUEST_HEADER_FIELDS_TOO_LARGE,
      Status.INTERNAL_SERVER_ERROR,
      Status.NOT_IMPLEMENTED,
      Status.BAD_GATEWAY,
      Status.SERVICE_UNAVAILABLE,
      Status.GATEWAY_TIMEOUT,
      Status.HTTP_VERSION_NOT_SUPPORTED,
      Status.VARIANT_ALSO_NEGOTIATES,
      Status.INSUFFICIENT_STORAGE,
      Status.NOT_EXTENDED,
      Status.NETWORK_AUTHENTICATION_REQUIRED,
    ),
  )

  def url: Gen[Random with Sized, URL] = for {
    path        <- HttpGen.path
    kind        <- HttpGen.location
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)
}
