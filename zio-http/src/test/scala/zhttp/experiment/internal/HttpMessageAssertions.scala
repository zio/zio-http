package zhttp.experiment.internal

import io.netty.handler.codec.http._
import zhttp.experiment.HttpEndpoint
import zhttp.experiment.HttpMessage.AnyResponse
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio.stream.ZStream
import zio.test.Assertion.anything
import zio.test.AssertionM.Render.param
import zio.test.{Assertion, TestResult, assertM}
import zio.{Chunk, Promise, Task, ZIO}

import java.nio.charset.Charset

trait HttpMessageAssertions {

  implicit final class HttpMessageSyntax(m: HttpObject) {
    def asString: String = m.toString.dropWhile(_ != '\n')
  }

  implicit final class HttpEndpointSyntax[R, E](app: HttpEndpoint[R, Throwable]) {
    def ===(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
      assertM(execute(app)(_.request("/")))(assertion)

    def getResponse: ZIO[R with EventLoopGroup, Throwable, HttpResponse] = getResponse()

    def getResponse(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    ): ZIO[R with EventLoopGroup, Throwable, HttpResponse] = for {
      proxy <- EndpointClient.deploy(app)
      _     <- proxy.request(url, method, header)
      _     <- proxy.end(content)
      res   <- proxy.receive
    } yield res.asInstanceOf[HttpResponse]

    def getContent: ZIO[R with EventLoopGroup, Throwable, String] = getContent()

    def getContent(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    ): ZIO[R with EventLoopGroup, Throwable, String] = ZStream
      .unwrap(for {
        proxy <- EndpointClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive

      } yield proxy.outbound.asStream.map(_.asInstanceOf[HttpContent]))
      .takeUntil(_.isInstanceOf[LastHttpContent])
      .filter(_ != LastHttpContent.EMPTY_LAST_CONTENT)
      .runCollect
      .map(_.map(_.content().toString(HTTP_CHARSET)).mkString(""))

    def getHttpContent: ZIO[R with EventLoopGroup, Throwable, Chunk[HttpContent]] = getHttpContent()

    def getHttpContent(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    ): ZIO[R with EventLoopGroup, Throwable, Chunk[HttpContent]] = ZStream
      .unwrap(for {
        proxy <- EndpointClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive

      } yield proxy.outbound.asStream.map(_.asInstanceOf[HttpContent]))
      .takeUntil(_.isInstanceOf[LastHttpContent])
      .runCollect

    def proxy: ZIO[R with EventLoopGroup, Throwable, EndpointClient] = EndpointClient.deploy(app)
  }

  def isResponse[A]: Assertion[A] = isResponse(anything)

  def isResponse[A](assertion: Assertion[HttpResponse]): Assertion[A] =
    Assertion.assertionRec("isResponse")(param(assertion))(assertion) {
      case msg: HttpResponse => Option(msg)
      case _                 => None
    }

  def isRequest[A](assertion: Assertion[Request]): Assertion[A] =
    Assertion.assertionRec("isRequest")(param(assertion))(assertion) {
      case msg: Request => Option(msg)
      case _            => None
    }

  def isContent: Assertion[Any] = isContent(anything)

  def isContent[A](assertion: Assertion[HttpContent]): Assertion[A] =
    Assertion.assertionRec("isContent")(param(assertion))(assertion) {
      case msg: HttpContent => Option(msg)
      case _                => None
    }

  def isLastContent[A](assertion: Assertion[LastHttpContent]): Assertion[A] =
    Assertion.assertionRec("isLastContent")(param(assertion))(assertion) {
      case msg: LastHttpContent => Option(msg)
      case _                    => None
    }

  def responseStatus(code: Int): Assertion[HttpResponse] =
    Assertion.assertion("status")(param(code))(_.status().code() == code)

  def responseStatus(status: Status): Assertion[HttpResponse] =
    Assertion.assertion("status")(param(status))(_.status().code() == status.asJava.code())

  def url(url: String): Assertion[Request] =
    Assertion.assertion("status")(param(url))(_.url.asString == url)

  def method(method: Method): Assertion[Request] =
    Assertion.assertion("method")(param(method))(_.method == method)

  def header(header: Header): Assertion[Request] =
    Assertion.assertion("header")(param(header))(_.headers.contains(header))

  def body[A](text: String, charset: Charset = Charset.defaultCharset()): Assertion[HttpContent] =
    Assertion.assertion("body")(param(text))(_.content.toString(charset) == text)

  def hasBody(data: String, charset: Charset = Charset.defaultCharset()): Assertion[HttpContent] =
    Assertion.assertion("body")(param(data))(_.content().toString(charset).contains(data))

  def responseHeader(name: String, value: String, ignoreCase: Boolean = true): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: $value"))(_.headers().contains(name, value, ignoreCase))

  def responseHeader(value: Header): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"${value.name}: ${value.value}"))(
      _.headers().contains(value.name, value.value, true),
    )

  def responseHeader(name: String): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: ???"))(_.headers().contains(name))

  def noHeader: Assertion[HttpResponse] = Assertion.assertion("no header")()(_.headers().size() == 0)

  def version(name: String): Assertion[HttpResponse] =
    Assertion.assertion("version")(param(name))(_.protocolVersion().toString == name)

  def isAnyResponse: Assertion[Any] = isResponse(anything)

  def execute[R](
    app: HttpEndpoint[R, Throwable],
  )(f: EndpointClient => Task[Any]): ZIO[R with EventLoopGroup, Throwable, HttpObject] =
    for {
      proxy <- EndpointClient.deploy(app)
      _     <- f(proxy)
      res   <- proxy.receive
    } yield res

  /**
   * Helper to create empty headers
   */
  def header = { new DefaultHttpHeaders() }

  /**
   * Dispatches a request with some content and asserts on the response received on an HttpEndpoint
   */
  def assertResponse[R](
    app: HttpEndpoint[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = Nil,
  )(
    assertion: Assertion[HttpObject],
  ): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      execute(app)(proxy =>
        proxy.request(url, method, header) *>
          ZIO.foreach_(content)(proxy.data(_)) *>
          proxy.end,
      ),
    )(assertion)

  def assertCompleteResponse[R](
    app: HttpEndpoint[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- EndpointClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        res   <- proxy.receive
      } yield res,
    )(assertion)

  def assertCompleteContent[R](
    app: HttpEndpoint[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- EndpointClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive
        data  <- proxy.receive
      } yield data,
    )(assertion)

  def assertBufferedContent[R](
    app: HttpEndpoint[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[String]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- EndpointClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive
        bytes <- proxy.receiveN(4).map(_.asInstanceOf[List[HttpContent]])
        str = bytes.map(_.content.toString(HTTP_CHARSET)).mkString("")
      } yield str,
    )(assertion)

  def assertBufferedResponse[R](
    app: HttpEndpoint[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- EndpointClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        res   <- proxy.receive
      } yield res,
    )(assertion)

  def getRequest(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  ): ZIO[Any with EventLoopGroup, Throwable, Request] = for {
    promise <- Promise.make[Nothing, Request]
    proxy   <- EndpointClient.deploy(HttpEndpoint.mount(Http.collectM[Request] { case a =>
      promise.succeed(a) as AnyResponse()
    }))
    _       <- proxy.request(url, method, header)
    _       <- proxy.end(content)
    request <- promise.await
  } yield request
}
