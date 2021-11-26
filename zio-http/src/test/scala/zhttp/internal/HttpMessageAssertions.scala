package zhttp.internal

import io.netty.handler.codec.http._
import zhttp.http._
import zhttp.service.{EventLoopGroup, Server}
import zio.stream.ZStream
import zio.test.Assertion.anything
import zio.test.AssertionM.Render.param
import zio.test.{Assertion, TestResult, assertM}
import zio.{Chunk, Promise, Task, ZIO}

import java.nio.charset.Charset

trait HttpMessageAssertions {

  def assertBufferedContent[R](
    app: HttpApp[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[String]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- HttpAppClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive
        bytes <- proxy.receiveN(4).map(_.asInstanceOf[List[HttpContent]])
        str = bytes.map(_.content.toString(HTTP_CHARSET)).mkString("")
      } yield str,
    )(assertion)

  def assertBufferedResponse[R](
    app: HttpApp[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- HttpAppClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        res   <- proxy.receive
      } yield res,
    )(assertion)

  def assertCompleteContent[R](
    app: HttpApp[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- HttpAppClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive
        data  <- proxy.receive
      } yield data,
    )(assertion)

  def assertCompleteResponse[R](
    app: HttpApp[R, Throwable],
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  )(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
    assertM(
      for {
        proxy <- HttpAppClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        res   <- proxy.receive
      } yield res,
    )(assertion)

  /**
   * Dispatches a request with some content and asserts on the response received on an HttpApp
   */
  def assertResponse[R](
    app: HttpApp[R, Throwable],
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

  def body[A](text: String, charset: Charset = Charset.defaultCharset()): Assertion[HttpContent] =
    Assertion.assertion("body")(param(text))(_.content.toString(charset) == text)

  def execute[R](
    app: HttpApp[R, Throwable],
  )(f: HttpAppClient => Task[Any]): ZIO[R with EventLoopGroup, Throwable, HttpObject] =
    for {
      proxy <- HttpAppClient.deploy(app)
      _     <- f(proxy)
      res   <- proxy.receive
    } yield res

  def getRequest(
    url: String = "/",
    method: HttpMethod = HttpMethod.GET,
    header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
    content: Iterable[String] = List("A", "B", "C", "D"),
  ): ZIO[Any with EventLoopGroup, Throwable, Request] = for {
    promise <- Promise.make[Nothing, Request]
    proxy   <- HttpAppClient.deploy(Http.collectM[Request] { case a =>
      promise.succeed(a) as Response()
    })
    _       <- proxy.request(url, method, header)
    _       <- proxy.end(content)
    request <- promise.await
  } yield request

  def hasBody(data: String, charset: Charset = Charset.defaultCharset()): Assertion[HttpContent] =
    Assertion.assertion("body")(param(data))(_.content().toString(charset).contains(data))

  def hasHeader(name: CharSequence, value: CharSequence, ignoreCase: Boolean = true): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: $value"))(_.headers().contains(name, value, ignoreCase))

  def hasHeader(value: Header): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"${value.name}: ${value.value}"))(
      _.headers().contains(value.name, value.value, true),
    )

  def header(header: Header): Assertion[Request] =
    Assertion.assertion("header")(param(header))(_.getHeaders.contains(header))

  /**
   * Helper to create empty headers
   */
  def header = { new DefaultHttpHeaders() }

  def isAnyResponse: Assertion[Any] = isResponse(anything)

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

  def isRequest[A](assertion: Assertion[Request]): Assertion[A] =
    Assertion.assertionRec("isRequest")(param(assertion))(assertion) {
      case msg: Request => Option(msg)
      case _            => None
    }

  def isResponse[A]: Assertion[A] = isResponse(anything)

  def isResponse[A](assertion: Assertion[HttpResponse]): Assertion[A] =
    Assertion.assertionRec("isResponse")(param(assertion))(assertion) {
      case msg: HttpResponse => Option(msg)
      case _                 => None
    }

  def method(method: Method): Assertion[Request] =
    Assertion.assertion("method")(param(method))(_.method == method)

  def noHeader: Assertion[HttpResponse] = Assertion.assertion("no header")()(_.headers().size() == 0)

  def responseHeaderName(name: String): Assertion[HttpResponse] =
    Assertion.assertion("header")(param(s"$name: ???"))(_.headers().contains(name))

  def responseStatus(code: Int): Assertion[HttpResponse] =
    Assertion.assertion("status")(param(code))(_.status().code() == code)

  def responseStatus(status: Status): Assertion[HttpResponse] =
    Assertion.assertion("status")(param(status))(_.status().code() == status.asJava.code())

  def url(url: String): Assertion[Request] =
    Assertion.assertion("status")(param(url))(_.url.asString == url)

  def version(name: String): Assertion[HttpResponse] =
    Assertion.assertion("version")(param(name))(_.protocolVersion().toString == name)

  implicit final class HttpMessageSyntax(m: HttpObject) {
    def asString: String = m.toString.dropWhile(_ != '\n')
  }

  implicit final class HttpAppSyntax[R, E](app: HttpApp[R, Throwable]) {
    def ===(assertion: Assertion[HttpObject]): ZIO[R with EventLoopGroup, Throwable, TestResult] =
      assertM(execute(app)(_.request("/")))(assertion)

    def getContent: ZIO[R with EventLoopGroup, Throwable, String] = getContent()

    def getContent(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    ): ZIO[R with EventLoopGroup, Throwable, String] = ZStream
      .unwrap(for {
        proxy <- HttpAppClient.deploy(app)
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
        proxy <- HttpAppClient.deploy(app)
        _     <- proxy.request(url, method, header)
        _     <- proxy.end(content)
        _     <- proxy.receive

      } yield proxy.outbound.asStream.map(_.asInstanceOf[HttpContent]))
      .takeUntil(_.isInstanceOf[LastHttpContent])
      .runCollect

    def getRequest(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    ): ZIO[R with EventLoopGroup, Throwable, Request] =
      for {
        p    <- Promise.make[Throwable, Request]
        c    <- HttpAppClient.deploy {
          Http.collectM[Request] { case req =>
            p.succeed(req).as(Response())
          }
        }
        _    <- c.request(url, method, header)
        _    <- c.end(content)
        data <- p.await
      } yield data

    def getRequestContent[R1 <: R, A](
      content: List[String] = List("A", "B", "C", "D"),
    ): ZIO[R1 with EventLoopGroup, Throwable, String] =
      for {
        p    <- Promise.make[Throwable, Chunk[Byte]]
        c    <- HttpAppClient.deploy {
          Http.fromPartialFunction[Request] { req =>
            for {
              res <- app(req)
              _   <- req.getBody.to(p)
            } yield res
          }
        }
        _    <- c.request()
        _    <- c.end(content)
        data <- p.await.map(cb => new String(cb.toArray, HTTP_CHARSET))
      } yield data

    def getResponse: ZIO[R with EventLoopGroup, Throwable, HttpResponse] = getResponse()

    def getResponse(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
      config: Server.Config[Any, Nothing] = Server.Config(),
    ): ZIO[R with EventLoopGroup, Throwable, HttpResponse] = for {
      proxy <- HttpAppClient.deploy(app, config)
      _     <- proxy.request(url, method, header)
      _     <- proxy.end(content)
      res   <- proxy.receive
    } yield res.asInstanceOf[HttpResponse]

    def getResponseCount(
      url: String = "/",
      method: HttpMethod = HttpMethod.GET,
      header: HttpHeaders = EmptyHttpHeaders.INSTANCE,
      content: Iterable[String] = List("A", "B", "C", "D"),
    ): ZIO[R with EventLoopGroup, Throwable, Int] = for {
      proxy <- HttpAppClient.deploy(app)
      _     <- proxy.request(url, method, header)
      _     <- proxy.end(content)
      count <- proxy.outbound.takeAll.map(_.count(_.isInstanceOf[HttpResponse]))
    } yield count

    def proxy: ZIO[R with EventLoopGroup, Throwable, HttpAppClient] = HttpAppClient.deploy(app)

  }
}
