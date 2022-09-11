package zio.http.service

import zio.http.Client.Config
import zio.http._
import zio.http.internal.{DynamicServer, HttpRunnableSpec}
import zio.http.middleware.Auth.Credentials
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, timeout}
import zio.test.assertZIO
import zio.{Scope, ZIO, durationInt}

import java.net.ConnectException

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live ++ Scope.default

  def clientSpec = suite("ClientSpec")(
    test("respond Ok") {
      val app = Http.ok.deploy.status.run()
      assertZIO(app)(equalTo(Status.Ok))
    },
    test("non empty content") {
      val app             = Http.text("abc")
      val responseContent = app.deploy.body.run().flatMap(_.asChunk)
      assertZIO(responseContent)(isNonEmpty)
    },
    test("echo POST request content") {
      val app = Http.collectZIO[Request] { case req => req.body.asString.map(Response.text(_)) }
      val res = app.deploy.body.mapZIO(_.asString).run(method = Method.POST, body = Body.fromString("ZIO user"))
      assertZIO(res)(equalTo("ZIO user"))
    },
    test("non empty content") {
      val app             = Http.empty
      val responseContent = app.deploy.body.run().flatMap(_.asString.map(_.length))
      assertZIO(responseContent)(isGreaterThan(0))
    },
    test("text content") {
      val app             = Http.text("zio user does not exist")
      val responseContent = app.deploy.body.mapZIO(_.asString).run()
      assertZIO(responseContent)(containsString("user"))
    },
    test("handle connection failure") {
      val res = Client.request("http://localhost:1").either
      assertZIO(res)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("handle proxy connection failure") {
      val res =
        for {
          validServerPort <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          serverUrl       <- ZIO.fromEither(URL.fromString(s"http://localhost:$validServerPort"))
          proxyUrl        <- ZIO.fromEither(URL.fromString("http://localhost:0001"))
          out             <- Client.request(
            Request(url = serverUrl),
            Config().withProxy(Proxy(proxyUrl)),
          )
        } yield out
      assertZIO(res.either)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("proxy respond Ok") {
      val res =
        for {
          port <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          url  <- ZIO.fromEither(URL.fromString(s"http://localhost:$port"))
          id   <- DynamicServer.deploy(Http.ok)
          proxy = Proxy.empty.withUrl(url).withHeaders(Headers(DynamicServer.APP_ID, id))
          out <- Client.request(
            Request(url = url),
            Config().withProxy(proxy),
          )
        } yield out
      assertZIO(res.either)(isRight)
    },
    test("proxy respond Ok for auth server") {
      val proxyAuthApp = Http.collect[Request] { case req =>
        val proxyAuthHeaderName = HeaderNames.proxyAuthorization.toString
        req.headers.toList.collectFirst { case (`proxyAuthHeaderName`, _) =>
          Response.ok
        }.getOrElse(Response.status(Status.Forbidden))
      }

      val res =
        for {
          port <- ZIO.environmentWithZIO[DynamicServer](_.get.port)
          url  <- ZIO.fromEither(URL.fromString(s"http://localhost:$port"))
          id   <- DynamicServer.deploy(proxyAuthApp)
          proxy = Proxy.empty
            .withUrl(url)
            .withHeaders(Headers(DynamicServer.APP_ID, id))
            .withCredentials(Credentials("test", "test"))
          out <- Client.request(
            Request(url = url),
            Config().withProxy(proxy),
          )
        } yield out
      assertZIO(res.either)(isRight)
    },
  )

  override def spec = {
    suite("Client") {
      serve(DynamicServer.app).as(List(clientSpec))
    }.provideLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
}
