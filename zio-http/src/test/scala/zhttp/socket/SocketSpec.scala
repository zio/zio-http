package zhttp.socket

import io.netty.handler.codec.http.HttpHeaderNames
import zhttp.experiment.internal.HttpMessageAssertions
import zhttp.http._
import zhttp.service.EventLoopGroup
import zio.console
import zio.stream.ZStream
import zio.test.{DefaultRunnableSpec, assertM}

object SocketSpec extends DefaultRunnableSpec with HttpMessageAssertions {

  private val env = EventLoopGroup.auto(1)

  // Message Handlers
  private val open = Socket.succeed(WebSocketFrame.text("hello!"))

  private val fooBar   = Socket.collect[WebSocketFrame] {
    case WebSocketFrame.Text("FOO") => ZStream.succeed(WebSocketFrame.text("BAR"))
    case WebSocketFrame.Text("BAR") => ZStream.succeed(WebSocketFrame.text("FOO"))
  }
  // Setup protocol settings
  private val protocol = SocketProtocol.subProtocol("json")

  // Setup decoder settings
  private val decoder = SocketDecoder.allowExtensions

  //  // Combine all channel handlers together
  private val socketApp: SocketApp[Any, Nothing] = (SocketApp.open(open) ++
    SocketApp.message(fooBar) ++ SocketApp.close(_ =>
      console.putStrLn("Closed!").ignore,
    ) ++ // Called after the connection is closed
    SocketApp.error(_ => console.putStrLn("Error!").ignore) ++ SocketApp.decoder(decoder) ++
    SocketApp.protocol(protocol)).asInstanceOf[SocketApp[Any, Nothing]]

//List(Header(Host,localhost:8090), Header(Connection,Upgrade), Header(Pragma,no-cache), Header(Cache-Control,no-cache), Header(User-Agent,Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/93.0.4577.63 Safari/537.36), Header(Upgrade,websocket), Header(Origin,chrome-extension://omalebghpgejjiaoknljcfmglgbpocdp), Header(Sec-WebSocket-Version,13), Header(Accept-Encoding,gzip, deflate, br), Header(Accept-Language,en-GB,en-US;q=0.9,en;q=0.8), Header(Sec-WebSocket-Key,d7o0CR9wn3sYtc/Xrx6otw==), Header(Sec-WebSocket-Extensions,permessage-deflate; client_max_window_bits))
  def spec =
    suite("WebSocketHandshakeHandler") {
      testM("should return response with 101 Status Code") {
        val app = HttpApp.collect {
          case req => {
            SocketResponse.from(socketApp = socketApp, req = req)
          }
        }
        assertM(
          app
            .getWebSocketResponse(header =
              Header.disassemble(
                List(
                  Header.custom(HttpHeaderNames.SEC_WEBSOCKET_KEY.toString(), "zX6y8r259tcsNtq/vPIePQ=="),
                  Header.custom(HttpHeaderNames.UPGRADE.toString(), "websocket"),
                  Header.custom(HttpHeaderNames.CONNECTION.toString(), "Upgrade"),
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
