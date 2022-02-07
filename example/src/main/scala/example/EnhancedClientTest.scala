package example

import zhttp.http._
import zhttp.service.client.DefaultClient
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

  // sequential execution test
  def triggerClientSequential(cl: DefaultClient) = for {
    req1 <- ZIO.effect("http://localhost:8081/foo/1")
    resp <- cl.run(req1)
    r1   <- resp.bodyAsString
    _    <- ZIO.effect(println(s"Response Content from $req1 ${r1.length} "))

    req3  <- ZIO.effect("http://www.google.com")
    resp3 <- cl.run(req3)
    //    resp3 <- cl.run("http://sports.api.decathlon.com/groups/water-aerobics")
    r3    <- resp3.bodyAsString
    _     <- ZIO.effect(println(s"Response Content from $req3  ${r3.length}"))

    req2  <- ZIO.effect("http://localhost:8081/bar/2")
    resp2 <- cl.run(req2)
    r2    <- resp2.bodyAsString
    _     <- ZIO.effect(println(s"Response Content  ${r2.length}"))

    resp4 <- cl.run("http://www.google.com")
    r4    <- resp4.bodyAsString
    _     <- ZIO.effect(println(s"Response Content  : ${r4.length}"))

    currActiveConn <- cl.connectionManager.getActiveConnections
    _              <- ZIO.effect(
      println(
        s"Number of active connections for four requests: $currActiveConn \n\n connections map ${cl.connectionManager.connRef}",
      ),
    )
  } yield ()

}
