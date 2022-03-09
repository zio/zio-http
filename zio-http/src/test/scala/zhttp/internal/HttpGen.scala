package zhttp.internal

import io.netty.buffer.Unpooled
import zhttp.http.Scheme.{HTTP, HTTPS, WS, WSS}
import zhttp.http.URL.Location
import zhttp.http._
import zhttp.service.Client.ClientRequest
import zio.random.Random
import zio.stream.ZStream
import zio.test.{Gen, Sized}
import zio.{Chunk, ZIO}

import java.io.File

object HttpGen {
  def clientParamsForFileHttpData(): Gen[Random with Sized, ClientRequest] = {
    for {
      file    <- Gen.fromEffect(ZIO.succeed(new File(getClass.getResource("/TestFile.txt").getPath)))
      method  <- HttpGen.method
      url     <- HttpGen.url
      headers <- Gen.listOf(HttpGen.header).map(Headers(_))
    } yield ClientRequest(url, method, headers, HttpData.fromFile(file))
  }

  def clientRequest[R](
    dataGen: Gen[R, HttpData],
    methodGen: Gen[R, Method] = HttpGen.method,
    urlGen: Gen[Random with Sized, URL] = HttpGen.url,
    headerGen: Gen[Random with Sized, Header] = HttpGen.header,
  ): Gen[R with Random with Sized, ClientRequest] =
    for {
      method  <- methodGen
      url     <- urlGen
      headers <- Gen.listOf(headerGen).map(Headers(_))
      data    <- dataGen
      version <- Gen.fromIterable(List(Version.Http_1_0, Version.Http_1_1))
    } yield ClientRequest(url, method, headers, data, version)

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

  def scheme: Gen[Any, Scheme] = Gen.fromIterable(List(HTTP, HTTPS, WS, WSS))

  def status: Gen[Any, Status] = Gen.fromIterable(
    List(
      Status.Continue,
      Status.Switching_Protocols,
      Status.Processing,
      Status.Ok,
      Status.Created,
      Status.Accepted,
      Status.Non_Authoritive_Information,
      Status.No_Content,
      Status.Reset_Content,
      Status.Partial_Content,
      Status.Multi_Status,
      Status.Multiple_Choices,
      Status.Moved_Permanently,
      Status.Found,
      Status.See_Other,
      Status.Not_Modified,
      Status.Use_Proxy,
      Status.Temporary_Redirect,
      Status.Permanent_Redirect,
      Status.Bad_Request,
      Status.Unauthorized,
      Status.Payment_Required,
      Status.Forbidden,
      Status.Not_Found,
      Status.Method_Not_Allowed,
      Status.Not_Acceptable,
      Status.Proxy_Authentication_Required,
      Status.Request_Timeout,
      Status.Conflict,
      Status.Gone,
      Status.Length_Required,
      Status.Precondition_Failed,
      Status.Request_Entity_Too_Large,
      Status.Request_Uri_Too_Long,
      Status.Unsupported_Media_Type,
      Status.Requested_Range_Not_Satisfiable,
      Status.Expectation_Failed,
      Status.Misdirected_Request,
      Status.Unprocessable_Entity,
      Status.Locked,
      Status.Failed_Dependency,
      Status.Unordered_Collection,
      Status.Upgrade_Required,
      Status.Precondition_Required,
      Status.Too_Many_Requests,
      Status.Request_Header_Fields_Too_Large,
      Status.Internal_Server_Error,
      Status.Not_Implemented,
      Status.Bad_Gateway,
      Status.Service_Unavailable,
      Status.Gateway_Timeout,
      Status.Http_Version_Not_Supported,
      Status.Variant_Also_Negotiates,
      Status.Insufficient_Storage,
      Status.Not_Extended,
      Status.Network_Authentication_Required,
    ),
  )

  def url: Gen[Random with Sized, URL] = for {
    path        <- HttpGen.path
    kind        <- HttpGen.location
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.listOf(Gen.alphaNumericString))
  } yield URL(path, kind, queryParams)

}
