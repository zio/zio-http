package example

import zhttp.http._
import zhttp.service.client.model.DefaultClient
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{Client, EventLoopGroup, Server}
import zio.{App, ExitCode, URIO, ZIO}

/**
 * Self contained enhanced Client demo with server.
 */
object EnhancedClientTest extends App {

  // Creating a test server
  private val PORT = 8081

  private val fooBar: HttpApp[Any, Nothing] = Http.collect[Request] {
    case Method.GET -> !! / "foo" / int(id) => Response.text("bar ----- " + id)
    case Method.GET -> !! / "bar" / int(id) => Response.text("foo foo foo foo foo foo foo foo foo  " + id)
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

  // Client Test code
  def clientTest = {
    for {
      client <- (
        Client.threads(2) ++
          Client.maxConnectionsPerRequestKey(10) ++
          Client.maxTotalConnections(20)
      ).make
      _      <- triggerClientSequential(client)
    } yield ()
  }

  val testUrl1  = "http://localhost:8081/foo/1"
  val testUrl2  = "http://localhost:8081/bar/2"
  val googleUrl = "http://www.google.com"

  // sequential execution test
  def triggerClientSequential(cl: DefaultClient) = for {
    resp <- cl.run(testUrl1).map(_.status)
    _    <- ZIO.effect(println(s"Response Status from $testUrl1 ${resp} "))

    resp3 <- cl.run(googleUrl).map(_.status)
    //    resp3 <- cl.run("http://sports.api.decathlon.com/groups/water-aerobics")
    _     <- ZIO.effect(println(s"Response Status from $googleUrl  ${resp3}"))

    resp2 <- cl.run(testUrl2).flatMap(_.bodyAsString)
    _     <- ZIO.effect(println(s"Response Content  ${resp2.length}"))

    resp4 <- cl.run(googleUrl).map(_.status)
    _     <- ZIO.effect(println(s"Response Status  : ${resp4}"))

    currActiveConn <- cl.connectionManager.getActiveConnections
    _              <- ZIO.effect(
      println(
        s"Number of active connections for four requests: $currActiveConn \n\n connections map ${cl.connectionManager.connRef}",
      ),
    )
  } yield ()

}
