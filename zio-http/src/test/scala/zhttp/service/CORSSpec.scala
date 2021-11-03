package zhttp.service

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.http._
//import zhttp.service.server.Transport
//import zio.ZManaged
import zio.test.Assertion._
import zio.test.assertM

object CORSSpec extends HttpRunnableSpec(8089) {

//  private val corsApp: HttpApp[Any, Throwable] =
//    CORS(HttpApp.collect { case Method.GET -> !! / "success" =>
//      Response.ok
//     })


  private val corsApp1 = serve {
    CORS(HttpApp.collect { case Method.GET -> !! / "success" =>
      Response.ok
    })
  }

//  private val server =
//    Server.port(8089) ++              // Setup port
//      Server.paranoidLeakDetection ++ // Paranoid leak detection (affects performance)
//      Server.app(corsApp)   ++  // Setup the Http app
//      Server.serverChannel(Transport.Auto)

  // combined ZLayer env for Client request
  val env = EventLoopGroup.auto() ++ ChannelFactory.auto // ++ ServerChannelFactory.auto

  override def spec =
    suite("CORS")(
      testM("OPTIONS request headers 1") {
        corsApp1
          .use{_ =>
            // Waiting for the server to start
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
          }
      }  +
        testM("OPTIONS request headers 2") {
          corsApp1
            .use{_ =>
              // Waiting for the server to start
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
            }
        }
      //      testM("Option Request status") {
//        server
//          .make
//          .use { _ =>
//            val actual = request(
//              !! / "success",
//              Method.OPTIONS,
//              "",
//              List[Header](
//                Header.custom(HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD.toString(), Method.GET.toString()),
//                Header.custom(HttpHeaderNames.ORIGIN.toString(), "Test-env"),
//              ),
//            )
//            assertM(actual.map(_.status))(
//              equalTo(
//                Status.NO_CONTENT,
//              ),
//            )
//          }
//      } +
//      testM("GET request") {
//        server
//          .make
//          .use { _ =>
//            val actual = headers(
//              !! / "success",
//              Method.GET,
//              "",
//              HttpHeaderNames.ACCESS_CONTROL_REQUEST_METHOD -> Method.GET.toString(),
//              HttpHeaderNames.ORIGIN                        -> "Test-env",
//            )
//            assertM(actual)(
//              hasSubset(
//                List[Header](
//                  Header.custom(HttpHeaderNames.ACCESS_CONTROL_EXPOSE_HEADERS.toString(), "*"),
//                  Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "Test-env"),
//                  Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS.toString(), Method.GET.toString()),
//                  Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_CREDENTIALS.toString(), "true"),
//                ),
//              ),
//            )
//          }
//      }
    ).provideCustomLayer(env)

}
