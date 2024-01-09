/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.internal

import java.io.File

import zio._
import zio.test.Gen

import zio.stream.ZStream

import zio.http.Header._
import zio.http.URL.Location
import zio.http._

object HttpGen {
  def anyPath: Gen[Any, Path] = for {
    flags    <- Gen.boolean.zip(Gen.boolean).map { case (left, right) =>
      var flags = 0
      if (left) flags = Path.Flag.LeadingSlash.add(flags)
      if (right) flags = Path.Flag.TrailingSlash.add(flags)
      flags
    }
    segments <- Gen.listOfBounded(0, 5)(
      Gen.oneOf(
        Gen.alphaNumericStringBounded(0, 5),
      ),
    )
  } yield Path(flags, Chunk.fromIterable(segments))

  def nonEmptyPath: Gen[Any, Path] = for {
    flags    <- Gen.boolean.zip(Gen.boolean).map { case (left, right) =>
      var flags = 0
      if (left) flags = Path.Flag.LeadingSlash.add(flags)
      if (right) flags = Path.Flag.TrailingSlash.add(flags)
      flags
    }
    segments <- Gen.listOfBounded(1, 5)(
      Gen.oneOf(
        Gen.alphaNumericStringBounded(0, 5),
      ),
    )
  } yield Path(flags, Chunk.fromIterable(segments))

  def clientParamsForFileBody(): Gen[Any, Request] = {
    for {
      file    <- Gen.fromZIO(ZIO.succeed(new File(getClass.getResource("/TestFile.txt").getPath)))
      method  <- HttpGen.method
      url     <- HttpGen.url
      headers <- Gen.listOf(HttpGen.header).map(Headers(_))
      version <- httpVersion
      body    <- Gen.fromZIO(Body.fromFile(file))
    } yield Request(version, method, url, headers, body, None)
  }

  private def clearSiteDataDirective: Gen[Any, ClearSiteDataDirective] = Gen.fromIterable(
    List(
      ClearSiteDataDirective.Cache,
      ClearSiteDataDirective.ClientHints,
      ClearSiteDataDirective.Cookies,
      ClearSiteDataDirective.Storage,
      ClearSiteDataDirective.ExecutionContexts,
      ClearSiteDataDirective.All,
    ),
  )

  def clearSiteData: Gen[Any, ClearSiteData] =
    Gen.chunkOfBounded(1, 5)(clearSiteDataDirective).map(c => ClearSiteData(NonEmptyChunk.fromChunk(c).get))

  def genAbsoluteLocation: Gen[Any, Location.Absolute] = for {
    scheme <- Gen.fromIterable(List(Scheme.HTTP, Scheme.HTTPS))
    host   <- Gen.alphaNumericStringBounded(1, 5)
    port   <- Gen.oneOf(Gen.const(80), Gen.const(443), Gen.int(0, 65536))
  } yield URL.Location.Absolute(scheme, host, Some(port))

  def genRelativeURL: Gen[Any, URL] = for {
    path        <- HttpGen.anyPath
    kind        <- HttpGen.genRelativeLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.chunkOf(Gen.alphaNumericString))
  } yield URL(path, kind, QueryParams(queryParams))

  def genAbsoluteURL: Gen[Any, URL] = for {
    path        <- HttpGen.nonEmptyPath
    kind        <- HttpGen.genAbsoluteLocation
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.chunkOf(Gen.alphaNumericString))
  } yield URL(path, kind, QueryParams(queryParams))

  def genRelativeLocation: Gen[Any, Location.Relative.type] = Gen.const(URL.Location.Relative)

  def header: Gen[Any, Header] = for {
    key   <- Gen.alphaNumericStringBounded(1, 4)
    value <- Gen.alphaNumericStringBounded(1, 4)
  } yield Header.Custom(key, value)

  def body[R](gen: Gen[R, List[String]]): Gen[R, Body] =
    for {
      list <- gen
      cnt  <- Gen
        .fromIterable(
          List(
            Body.fromStreamChunked(
              ZStream.fromIterable(list, chunkSize = 2).map(b => Chunk.fromArray(b.getBytes())).flattenChunks,
            ),
            Body.fromString(list.mkString("")),
            Body.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            Body.fromArray(list.mkString("").getBytes()),
            Body.empty,
          ),
        )
    } yield cnt

  def httpVersion: Gen[Any, Version] =
    Gen.fromIterable(List(Version.Http_1_0, Version.Http_1_1))

  def location: Gen[Any, URL.Location] = {
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
            Body.fromStreamChunked(
              ZStream.fromIterable(list, chunkSize = 2).map(b => Chunk.fromArray(b.getBytes())).flattenChunks,
            ),
            Body.fromString(list.mkString("")),
            Body.fromChunk(Chunk.fromArray(list.mkString("").getBytes())),
            Body.fromArray(list.mkString("").getBytes()),
          ),
        )
    } yield cnt

  def request: Gen[Any, Request] = for {
    version <- httpVersion
    method  <- HttpGen.method
    url     <- HttpGen.url
    headers <- Gen.listOf(HttpGen.header).map(Headers(_))
    data    <- HttpGen.body(Gen.listOf(Gen.alphaNumericString))
  } yield Request(version, method, url, headers, data, None)

  def requestGen[R](
    dataGen: Gen[R, Body],
    methodGen: Gen[R, Method] = HttpGen.method,
    urlGen: Gen[Any, URL] = HttpGen.url,
    headerGen: Gen[Any, Header] = HttpGen.header,
  ): Gen[R with Any, Request] =
    for {
      method  <- methodGen
      url     <- urlGen
      headers <- Gen.listOf(headerGen).map(Headers(_))
      data    <- dataGen
      version <- httpVersion
    } yield Request(version, method, url, headers, data, None)

  def response[R](gContent: Gen[R, List[String]]): Gen[Any with R, Response] = {
    for {
      content <- HttpGen.body(gContent)
      headers <- HttpGen.header
      status  <- HttpGen.status
    } yield Response(status, Headers(headers), content)
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

  def url: Gen[Any, URL] = for {
    start <- Gen.elements(Path.empty, Path.root)
    mid   <- Gen.elements("a", "a/b", "a/b/c")
    last  <- Gen.elements(Path.empty, Path.root)
    path = start ++ Path(mid) ++ last
    kind        <- HttpGen.location
    queryParams <- Gen.mapOf(Gen.alphaNumericString, Gen.chunkOf(Gen.alphaNumericString))
  } yield URL(path, kind, QueryParams(queryParams))

  def acceptEncodingSingleValue(weight: Option[Double]): Gen[Any, AcceptEncoding] = Gen.fromIterable(
    List(
      AcceptEncoding.GZip(weight),
      AcceptEncoding.Deflate(weight),
      AcceptEncoding.Br(weight),
      AcceptEncoding.Identity(weight),
      AcceptEncoding.Compress(weight),
      AcceptEncoding.NoPreference(weight),
    ),
  )

  def acceptEncodingSingleValueWithWeight: Gen[Any, AcceptEncoding] = for {
    weight <- Gen.option(Gen.double(0.1, 1.0))
    value  <- acceptEncodingSingleValue(weight)
  } yield value

  def acceptEncoding: Gen[Any, AcceptEncoding] =
    Gen
      .chunkOf1(acceptEncodingSingleValueWithWeight)
      .map(ecs =>
        if (ecs.size == 1) ecs.head
        else AcceptEncoding.Multiple(ecs),
      )

  def cacheControlSingleValue(seconds: Int): Gen[Any, CacheControl] =
    Gen.fromIterable(
      List(
        CacheControl.Immutable,
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

  def cacheControl: Gen[Any, CacheControl] =
    Gen
      .chunkOf1(cacheControlSingleValueWithSeconds)
      .map(ccs =>
        if (ccs.size == 1) ccs.head
        else CacheControl.Multiple(ccs),
      )

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
    Gen.chunkOfBounded(1, 9)(method).map(chunk => Allow(NonEmptyChunk.fromChunk(chunk).get))

  def connectionHeader: Gen[Any, Connection] =
    Gen.elements(Connection.Close, Connection.KeepAlive)

  def allowContentEncodingSingleValue: Gen[Any, ContentEncoding] = Gen.fromIterable(
    List(
      ContentEncoding.Br,
      ContentEncoding.Compress,
      ContentEncoding.GZip,
      ContentEncoding.Multiple(NonEmptyChunk(ContentEncoding.Br, ContentEncoding.Compress)),
      ContentEncoding.Deflate,
    ),
  )

  def acceptRanges: Gen[Any, AcceptRanges] =
    Gen.elements(AcceptRanges.Bytes, AcceptRanges.None)

  def allowTransferEncodingSingleValue: Gen[Any, TransferEncoding] = Gen.fromIterable(
    List(
      TransferEncoding.Chunked,
      TransferEncoding.Compress,
      TransferEncoding.GZip,
      TransferEncoding.Multiple(NonEmptyChunk(TransferEncoding.Chunked, TransferEncoding.Compress)),
      TransferEncoding.Deflate,
    ),
  )

  def headerNames: Gen[Any, List[String]]           = Gen.listOf(Gen.alphaNumericStringBounded(2, 200))
  def headerNames1: Gen[Any, NonEmptyChunk[String]] = Gen.chunkOf1(Gen.alphaNumericStringBounded(2, 200))

  def authSchemes: Gen[Any, AuthenticationScheme] =
    Gen.elements(
      AuthenticationScheme.Basic,
      AuthenticationScheme.Bearer,
      AuthenticationScheme.Digest,
      AuthenticationScheme.HOBA,
      AuthenticationScheme.Mutual,
      AuthenticationScheme.Negotiate,
      AuthenticationScheme.OAuth,
      AuthenticationScheme.ScramSha1,
      AuthenticationScheme.ScramSha256,
      AuthenticationScheme.Vapid,
    )
}
