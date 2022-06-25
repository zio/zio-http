package zhttp.service
import zhttp.http._
import zhttp.http.middleware.Auth.Credentials
import zhttp.internal.{DynamicServer, HttpRunnableSpec}
import zhttp.service.Client.Config
import zhttp.service.server._
import zio.ZIO
import zio.duration.durationInt
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, timeout}
import zio.test._

import java.net.ConnectException

object ClientSpec extends HttpRunnableSpec {

  private val env =
    EventLoopGroup.nio() ++ ChannelFactory.nio ++ ServerChannelFactory.nio ++ DynamicServer.live

  def clientSpec = suite("ClientSpec") {
    testM("respond Ok") {
      val app = Http.ok.deploy.status.run()
      assertM(app)(equalTo(Status.Ok))
    } +
      testM("non empty content") {
        val app             = Http.text("abc")
        val responseContent = app.deploy.body.run()
        assertM(responseContent)(isNonEmpty)
      } +
      testM("echo POST request content") {
        val app = Http.collectZIO[Request] { case req => req.bodyAsString.map(Response.text(_)) }
        val res = app.deploy.bodyAsString.run(method = Method.POST, content = HttpData.fromString("ZIO user"))
        assertM(res)(equalTo("ZIO user"))
      } +
      testM("non empty content") {
        val app             = Http.empty
        val responseContent = app.deploy.body.run().map(_.length)
        assertM(responseContent)(isGreaterThan(0))
      } +
      testM("text content") {
        val app             = Http.text("zio user does not exist")
        val responseContent = app.deploy.bodyAsString.run()
        assertM(responseContent)(containsString("user"))
      } +
      testM("handle connection failure") {
        val res = Client.request("http://localhost:1").either
        assertM(res)(isLeft(isSubtype[ConnectException](anything)))
      } +
      testM("handle proxy connection failure") {
        val res =
          for {
            validServerPort <- ZIO.accessM[DynamicServer](_.get.port)
            serverUrl       <- ZIO.fromEither(URL.fromString(s"http://localhost:$validServerPort"))
            proxyUrl        <- ZIO.fromEither(URL.fromString("http://localhost:0001"))
            out             <- Client.request(
              Request(url = serverUrl),
              Config().withProxy(Proxy(proxyUrl)),
            )
          } yield out
        assertM(res.either)(isLeft(isSubtype[ConnectException](anything)))
      } +
      testM("proxy respond Ok") {
        val res =
          for {
            port <- ZIO.accessM[DynamicServer](_.get.port)
            url  <- ZIO.fromEither(URL.fromString(s"http://localhost:$port"))
            id   <- DynamicServer.deploy(Http.ok)
            proxy = Proxy.empty.withUrl(url).withHeaders(Headers(DynamicServer.APP_ID, id))
            out <- Client.request(
              Request(url = url),
              Config().withProxy(proxy),
            )
          } yield out
        assertM(res.either)(isRight)
      } +
      testM("proxy respond Ok for auth server") {
        val proxyAuthApp = Http.collect[Request] { case req =>
          val proxyAuthHeaderName = HeaderNames.proxyAuthorization.toString
          req.headers.toList.collectFirst { case (`proxyAuthHeaderName`, _) =>
            Response.ok
          }.getOrElse(Response.status(Status.Forbidden))
        }

        val res =
          for {
            port <- ZIO.accessM[DynamicServer](_.get.port)
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
        assertM(res.either)(isRight)
      }
  }

  override def spec = {
    suiteM("Client") {
      serve(DynamicServer.app).as(List(clientSpec)).useNow
    }.provideCustomLayerShared(env) @@ timeout(5 seconds) @@ sequential
  }
}
