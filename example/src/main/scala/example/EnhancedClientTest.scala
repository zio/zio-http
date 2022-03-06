package example

import zhttp.http._
import zhttp.service.client.domain.DefaultClient
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{Client, ClientSettings, EventLoopGroup, Server}
import zio.{App, ExitCode, URIO, ZIO}

/**
 * Self contained enhanced Client demo with server.
 */
object EnhancedClientTest extends App {

  // Creating a test server
  private val PORT = 8081

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" / int(id) => Response.text("bar ----- " + id)
    case Method.GET -> !! / "bar" / int(id) => Response.text("foo ***** " + id)
  }

  private val server =
    Server.port(PORT) ++ // Setup port
      Server.app(fooBar) // Setup the Http app

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {
    server.make
      .use(_ => clientTest)
      .provideCustomLayer(ServerChannelFactory.auto ++ EventLoopGroup.auto(2))
      .exitCode
  }

  val testUrl1 = "http://localhost:8081/foo/1"
  val testUrl2 = "http://localhost:8081/bar/2"

  val clientSettings = ClientSettings.threads(8)

  // Client Test code
  def clientTest = {
    for {
      client <- Client.make(clientSettings)
      _      <- triggerClientSequential(client)
    } yield ()
  }

  /*
    SEQUENTIAL EXECUTION TEST
   */
  def triggerClientSequential(cl: DefaultClient) = for {
    resp <- cl.run(testUrl1).flatMap(_.bodyAsString)
    _    <- ZIO.effect(println(s"Response Status from $testUrl1 ${resp} \n ----- \n"))

    resp2 <- cl.run(testUrl2).flatMap(_.bodyAsString)
    _     <- ZIO.effect(println(s"Response Content from $testUrl2 ${resp2} \n ----- \n"))
  } yield ()

}
