package example

import zhttp.http._
import zhttp.service.client.model.DefaultClient
import zhttp.service.server.ServerChannelFactory
import zhttp.service.{ClientSettings, EventLoopGroup, Server}
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
        ClientSettings.threads(2) ++
          ClientSettings.maxConnectionsPerRequestKey(10) ++
          ClientSettings.maxTotalConnections(20)
      ).make
//      _      <- triggerClientSequential(client)
      _      <- triggerParallel(client)
    } yield ()
  }

  val testUrl1  = "http://localhost:8081/foo/1"
  val testUrl2  = "http://localhost:8081/bar/2"
  val googleUrl = "http://www.google.com"
  val bbcUrl = "http://www.dream11.com"

  // sequential execution test
  def triggerClientSequential(cl: DefaultClient) = for {
    resp <- cl.run(testUrl1).flatMap(_.bodyAsString)
    _    <- ZIO.effect(println(s"Response Status from $testUrl1 ${resp.length} \n ----- \n"))

//    resp3 <- cl.run(googleUrl).map(_.status)
//    //    resp3 <- cl.run("http://sports.api.decathlon.com/groups/water-aerobics")
//    _     <- ZIO.effect(println(s"\n \n Response Status from $googleUrl  ${resp3} \n ----- \n"))

    resp2 <- cl.run(testUrl2).flatMap(_.bodyAsString)
    _     <- ZIO.effect(println(s"Response Content from $testUrl2 ${resp2.length} \n ----- \n"))

//    resp4 <- cl.run(googleUrl).map(_.status)
//    _     <- ZIO.effect(println(s"Response Status AGAIN From $googleUrl : ${resp4} \n ----- \n"))

    currActiveConn <- cl.connectionManager.getActiveConnections
    idleMap <- cl.connectionManager.connectionState.idleConnectionsMap.get
    _              <- ZIO.effect(
      println(
        s"Number of active connections for four requests: $currActiveConn \n\n connections map ${idleMap}",
      ),
    )
  } yield ()

  def triggerParallel(cl: DefaultClient) = for {
//    p1 <- cl.run(testUrl1).flatMap(_.bodyAsString).fork
//    p2 <- cl.run(testUrl2).flatMap(_.bodyAsString).fork
//    res <- (p1 zip p2).join

        res <- zio.Task.foreachPar(Set(bbcUrl,googleUrl))(v => cl.run(v).flatMap(_.bodyAsString.map(_.length)))
//    res <- zio.Task.foreachPar(Set(testUrl1,googleUrl))(v => cl.run(v).flatMap(_.bodyAsString))
//    res <- zio.Task.foreachPar(Set(testUrl1,testUrl2)) {
//      v => Thread.sleep(3000); cl.run(v).flatMap(_.bodyAsString)
//    }
//    res <- zio.Task.foreachPar((1 to 2).toList)(_ => cl.run(googleUrl).flatMap(_.bodyAsString))
//    _ <- ZIO.effect(println(s"\n\n RESULTS OF PARALLEL EXECUTION $c \n\n"))

//    c <- zio.Task.foreachPar(List(testUrl1, testUrl2))(v => cl.run(v).flatMap(_.bodyAsString))
    currActiveConn <- cl.connectionManager.getActiveConnections
    _ <- ZIO.effect(println(s"\n\n PARALLEL EXECUTION \n RESPONSE: $res \n CURRENT CONNECTIONS $currActiveConn \n\n"))
  } yield ()
}
