package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.cors
import zio.test.Assertion._
import zio.test.assertM

object CORSSpec extends HttpRunnableSpec(8089) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto

  val app = serveWithPort {
    HttpApp.collect { case Method.GET -> !! / "success" =>
      Response.ok
    } @@ cors()
  } _

  override def spec = suite("CORS")(
    testM("OPTIONS request headers") {
      val p = 8089
      app(8089).use { _ =>
        val actual = requestWithPort(
          p,
          !! / "success",
          Method.OPTIONS,
          "",
          List[Header](
            Header.custom(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString(), Method.GET.toString()),
            Header.custom(HttpHeaderNames.ORIGIN.toString(), "Test-env"),
          ),
        )
        assertM(actual.map(_.headers))(
          hasSubset(
            List(
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), "true"),
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(), Method.GET.toString()),
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "Test-env"),
              Header.custom(
                HttpHeaderNames.ACCESS_CONTROL_ALLOW_HEADERS.toString(),
                CORS.DefaultCORSConfig.allowedHeaders.get.mkString(","),
              ),
            ),
          ),
        )
      }
    },
    testM("Option Request status") {
      val p = 8090
      app(p).use { _ =>
        val actual = requestWithPort(
          p,
          !! / "success",
          Method.OPTIONS,
          "",
          List[Header](
            Header.custom(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString(), Method.GET.toString()),
            Header.custom(HttpHeaderNames.ORIGIN.toString(), "Test-env"),
          ),
        )
        assertM(actual.map(_.status))(
          equalTo(
            Status.NO_CONTENT,
          ),
        )
      }
    },
    testM("GET request") {
      val p = 8091
      app(p).use { _ =>
        val actual = headersWithPort(
          p,
          !! / "success",
          Method.GET,
          "",
          HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD -> Method.GET.toString(),
          HttpHeaderNames.ORIGIN                        -> "Test-env",
        )
        assertM(actual)(
          hasSubset(
            List[Header](
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), "*"),
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "Test-env"),
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(), Method.GET.toString()),
              Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), "true"),
            ),
          ),
        )
      }
    },
  ).provideCustomLayer(env)
}
