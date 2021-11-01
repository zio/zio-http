package zhttp.experiment.internal

import io.netty.buffer.Unpooled
import zhttp.http._
import zio.Chunk
import zio.random.Random
import zio.stream.ZStream
import zio.test.{Gen, Sized}

object HttpGen {
  val status: Gen[Any, Status] = Gen.fromIterable(
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

  def httpData[R](gen: Gen[R, List[String]]): Gen[R, HttpData[Any, Nothing]] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            HttpData.fromStream(ZStream.fromIterable(list).map(b => Chunk.fromArray(b.getBytes())).flattenChunks),
            HttpData.fromText(list.mkString("")),
            HttpData.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            HttpData.fromByteBuf(Unpooled.copiedBuffer(list.mkString(""), HTTP_CHARSET)),
            HttpData.empty,
          ),
        )
    } yield cnt

  def nonEmptyHttpData[R](gen: Gen[R, List[String]]): Gen[R, HttpData[Any, Nothing]] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            HttpData.fromStream(ZStream.fromIterable(list).map(b => Chunk.fromArray(b.getBytes())).flattenChunks),
            HttpData.fromText(list.mkString("")),
            HttpData.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            HttpData.fromByteBuf(Unpooled.copiedBuffer(list.mkString(""), HTTP_CHARSET)),
          ),
        )
    } yield cnt

  def header: Gen[Random with Sized, Header] = for {
    key   <- Gen.alphaNumericStringBounded(1, 4)
    value <- Gen.alphaNumericStringBounded(1, 4)
  } yield Header(key, value)

  def response[R](gContent: Gen[R, List[String]]): Gen[Random with Sized with R, Response[Any, Nothing]] = {
    for {
      content <- HttpGen.httpData(gContent)
      headers <- HttpGen.header.map(List(_))
      status  <- HttpGen.status
    } yield Response(status, headers, content)
  }

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
  } yield Cookie(name, content, expires, domain, path, secure, httpOnly, maxAge, sameSite)

  def reqCookies: Gen[Random with Sized, (List[Cookie], String)] = {
    for {
      name         <- Gen.anyString
      content      <- Gen.anyString
      cookieList   <- Gen.listOfN(3)(Gen.const(Cookie(name, content)))
      cookieString <- Gen.const(cookieList.map(x => s"${x.name}=${x.content}").mkString(";"))
    } yield (cookieList, cookieString)
  }

  def path: Gen[Random with Sized, Path] = {
    for {
      l <- Gen.listOf(Gen.alphaNumericString)
      p <- Gen.const(Path(l))
    } yield p
  }
}
