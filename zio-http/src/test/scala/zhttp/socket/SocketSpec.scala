package zhttp.socket

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.experiment.internal.HttpMessageAssertions
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio.test.{DefaultRunnableSpec, assertM}

object SocketSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  private val env = EventLoopGroup.auto(1)

  // Message Handlers
  private val open = Socket.succeed(WebSocketFrame.text("hello!"))

  //  // Combine all channel handlers together
  private val socketApp = SocketApp.open(open) // Called after the request is successfully upgraded to websocket

  def spec =
    suite("WebSocketHandshakeHandler") {
      testM("should return response with 101 Status Code") {
        val app = HttpApp.collect {
          case req @ Method.GET -> !! / "subscriptions" => {
            SocketResponse.from(socketApp = socketApp, req = req)
          }
        }
        assertM(
          app
            .getWebSocketResponse(header =
              Header.disassemble(
                List(
                  Header.custom(HttpHeaderNames.SEC_WEBSOCKET_KEY.toString(), "zX6y8r259tcsNtq/vPIePQ=="),
                  Header.custom(HttpHeaderNames.HOST.toString(), "localhost:8090"),
                  Header.custom(
                    HttpHeaderNames.ORIGIN.toString(),
                    "chrome-extension://omalebghpgejjiaoknljcfmglgbpocdp",
                  ),
//                  Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "*"),
                  Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
                  Header.custom(HttpHeaderNames.CONNECTION.toString(), "upgrade"),
                  Header.custom(HttpHeaderNames.SEC_WEBSOCKET_VERSION.toString(), "13"),
                ),
              ),
            ),
        )(isResponse {
          responseStatus(Status.SWITCHING_PROTOCOLS) &&
          version("HTTP/1.1")
        })
      }
//        testM("should return WebSocketFrame") {
//          val app = HttpApp.collect {
//            case req if req.isValidWebSocketRequest => {
//              SocketResponse.from(socketApp = socketApp, req = req)
//            }
//          }
//          assertM(
//            app
//              .getWebSocketFrame(header =
//                Header.disassemble(
//                  List(
//                    Header.custom(HttpHeaderNames.SEC_WEBSOCKET_KEY.toString(), "zX6y8r259tcsNtq/vPIePQ=="),
//                    Header.custom(HttpHeaderNames.HOST.toString(), "localhost:8090"),
//                    Header.custom(
//                      HttpHeaderNames.ORIGIN.toString(),
//                      "chrome-extension://omalebghpgejjiaoknljcfmglgbpocdp",
//                    ),
//                    Header.custom(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN.toString(), "*"),
//                    Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
//                    Header.custom(HttpHeaderNames.CONNECTION.toString(), "upgrade"),
//                    Header.custom(HttpHeaderNames.SEC_WEBSOCKET_VERSION.toString(), "13"),
//                  ),
//                ),
//              ),
//          )(isResponse {
//            responseStatus(Status.SWITCHING_PROTOCOLS) &&
//            version("HTTP/1.1")
//          })
//        }
    }.provideCustomLayer(env)
}
