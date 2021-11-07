package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.cors
import zhttp.service.server._
import zio.ZManaged
import zio.test.Assertion._
import zio.test.assertM

object CORSSpec extends HttpRunnableSpec(8089) {
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto ++ ServerChannelFactory.auto

  val app: ZManaged[EventLoopGroup with ServerChannelFactory, Nothing, Unit] = serve {
    HttpApp.collect { case Method.GET -> !! / "success" =>
      Response.ok
    } @@ cors()
  }

  override def spec = suiteM("CORS")(
    app
      .as(
        List(
          testM("OPTIONS request headers") {
            val actual = request(
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
          } +
            testM("Option Request status") {
              val actual = request(
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
            } +
            testM("GET request") {
              val actual = headers(
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
            },
        ),
      )
      .useNow,
  ).provideCustomLayer(env)
}
