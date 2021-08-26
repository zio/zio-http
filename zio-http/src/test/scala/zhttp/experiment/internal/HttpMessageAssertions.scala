package zhttp.experiment.internal

import io.netty.buffer.ByteBuf
import io.netty.handler.codec.http._
import zhttp.experiment.HttpMessage.{AnyRequest, AnyResponse}
import zhttp.experiment.ServerEndpoint.CanDecode
import zhttp.experiment.{BufferedRequest, CompleteRequest, HttpEndpoint}
import zhttp.http.{HTTP_CHARSET, Header, Http, Method, Status}
import zhttp.service.EventLoopGroup
import zio.stream.ZStream
import zio.test.Assertion.anything
import zio.test.AssertionM.Render.param
import zio.test.{Assertion, TestResult, assert, assertM}
import zio.{Chunk, Promise, Task, ZIO}

import java.nio.charset.Charset

trait HttpMessageAssertions {
  type Buffered = BufferedRequest[ByteBuf]
  type Complete = CompleteRequest[ByteBuf]

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

  def isRequest[A](assertion: Assertion[AnyRequest]): Assertion[A] =
    Assertion.assertionRec("isRequest")(param(assertion))(assertion) {
      case msg: AnyRequest => Option(msg)
      case _               => None
    }

  def isCompleteRequest[A](assertion: Assertion[CompleteRequest[ByteBuf]]): Assertion[A] =
    Assertion.assertionRec("isCompleteRequest")(param(assertion))(assertion) {
      case msg: CompleteRequest[_] if msg.content.isInstanceOf[ByteBuf] =>
        Option(msg.asInstanceOf[CompleteRequest[ByteBuf]])
      case _                                                            => None
    }

  def isBufferedRequest[A](assertion: Assertion[BufferedRequest[ByteBuf]]): Assertion[A] =
    Assertion.assertionRec("isBufferedRequest")(param(assertion))(assertion) {
      case msg: BufferedRequest[_] => Option(msg.asInstanceOf[BufferedRequest[ByteBuf]])
      case _                       => None
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

  def requestBody[A](text: String, charset: Charset = HTTP_CHARSET): Assertion[CompleteRequest[ByteBuf]] =
    Assertion.assertion("requestBody")(param(text))(_.content.toString(charset) == text)

  def url(url: String): Assertion[AnyRequest] =
    Assertion.assertion("status")(param(url))(_.url.asString == url)

  def method(method: Method): Assertion[AnyRequest] =
    Assertion.assertion("method")(param(method))(_.method == method)

  def header(header: Header): Assertion[AnyRequest] =
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
   * Creates an HttpEndpoint internally that requires a BufferedRequest. Allows asserting on any field of the request
   * using the `assertion` parameter.
   */
  def assertBufferedRequest[R, E](
    f: EndpointClient => ZIO[R, E, Any],
  )(
    assertion: Assertion[BufferedRequest[ByteBuf]],
  ): ZIO[EventLoopGroup with R, E, TestResult] = for {
    promise <- Promise.make[Nothing, BufferedRequest[ByteBuf]]
    proxy   <- EndpointClient.deploy(
      HttpEndpoint.mount(Http.collectM[BufferedRequest[ByteBuf]](req => promise.succeed(req) as AnyResponse())),
    )
    _       <- f(proxy)
    req     <- promise.await
  } yield assert(req)(assertion)

  /**
   * Creates an HttpEndpoint internally that requires a BufferedRequest. Allows asserting on the content of the request
   * using the `assertion` parameter.
   */
  def assertBufferedRequestContent(
    url: String = "/",
    method: HttpMethod = HttpMethod.POST,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = Nil,
  )(
    assertion: Assertion[Iterable[String]],
  ): ZIO[EventLoopGroup, Throwable, TestResult] = for {
    promise <- Promise.make[Nothing, Chunk[ByteBuf]]
    proxy   <- EndpointClient.deploy(
      HttpEndpoint.mount(
        Http.collectM[BufferedRequest[ByteBuf]](req => req.content.runCollect.tap(promise.succeed) as AnyResponse()),
      ),
    )

    _ <- proxy.request(url, method, header)
    _ <- proxy.end(content)

    req <- promise.await
  } yield assert(req.toList.map(bytes => bytes.toString(HTTP_CHARSET)))(assertion)

  /**
   * Creates an HttpEndpoint internally that requires a BufferedRequest. Allows asserting on any field of the request
   * using the `assertion` parameter.
   */
  def assertBufferedRequest(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = Nil,
  )(
    assertion: Assertion[BufferedRequest[ByteBuf]],
  ): ZIO[EventLoopGroup, Throwable, TestResult] =
    assertBufferedRequest(proxy =>
      proxy.request(url, method, header) *>
        ZIO.foreach_(content)(proxy.data(_)) *>
        proxy.end,
    )(assertion)

  /**
   * Creates an HttpEndpoint internally that requires a CompleteRequest. The request to be sent can be configured via
   * the `f` function. Allows any kind of custom assertion on the CompleteRequest.
   */
  def assertCompleteRequest[R, E](f: EndpointClient => ZIO[R, E, Any])(
    assertion: Assertion[CompleteRequest[ByteBuf]],
  ): ZIO[EventLoopGroup with R, E, TestResult] = for {
    promise <- Promise.make[Nothing, CompleteRequest[ByteBuf]]
    proxy   <- EndpointClient.deploy(
      HttpEndpoint.mount(Http.collectM[CompleteRequest[ByteBuf]](req => promise.succeed(req) as AnyResponse())),
    )
    _       <- f(proxy)
    req     <- promise.await
  } yield assert(req)(assertion)

  /**
   * Creates an HttpEndpoint internally that requires a CompleteRequest. Allows asserting on any field of the request
   * using the `assertion` parameter.
   */
  def assertCompleteRequest(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = Nil,
  )(
    assertion: Assertion[CompleteRequest[ByteBuf]],
  ): ZIO[EventLoopGroup, Throwable, TestResult] =
    assertCompleteRequest(proxy =>
      proxy.request(url, method, header) *>
        ZIO.foreach(content)(proxy.data(_)) *>
        proxy.end,
    )(assertion)

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

  def getRequest[A]: GetRequest[A] = new GetRequest[A](())

  final class GetRequest[A](val unit: Unit) {
    def apply(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    )(implicit ev: CanDecode[A]): ZIO[Any with EventLoopGroup, Throwable, A] = for {
      promise <- Promise.make[Nothing, A]
      proxy   <- EndpointClient.deploy(HttpEndpoint.mount(Http.collectM[A] { case a =>
        promise.succeed(a) as AnyResponse()
      }))
      _       <- proxy.request(url, method, header)
      _       <- proxy.end(content)
      request <- promise.await
    } yield request
  }
}
