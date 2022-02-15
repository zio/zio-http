package example

import zhttp.http.{Method, URL}
import zhttp.service.Client.ClientRequest
import zhttp.service.client.model.DefaultClient
import zhttp.service.{Client, ClientSettings}
import zio._
import zio.console.putStrLn
import zio.stream.{ZStream, ZTransducer}

object ClientLoadTest extends App {

//  val env = ChannelFactory.auto ++ EventLoopGroup.auto()

  val sleep = "http://localhost:8080/healthcheck"

  var count = 0
  val client                                      = Client.make(ClientSettings.threads(8))
  def get(url: URL, defaultClient: DefaultClient) = {
    for {
      resp <- defaultClient.run(ClientRequest(url, Method.GET))
      body <- resp.bodyAsString
      res = (body, resp.status.asJava.code())
    } yield res
  }
//    Client
//      .request(ClientRequest(url, Method.GET))
//      .flatMap(result => result.bodyAsString.map((_, result.status.asJava.code)))

  def stream(url: URL, batchSize: Int, defaultClient: DefaultClient) =
    ZStream
      .repeat(())
      .transduce(
        ZTransducer.fromEffect(
          ZIO.collectAllPar(List.fill(batchSize)(get(url, defaultClient))),
        ),
      )

  val app = for {
    cl  <- Client.make(ClientSettings.threads(8))
    url <- ZIO.fromEither(URL.fromString(sleep))
    _   <- stream(url, 50, cl).zipWithIndex.foreach {
      s =>
        count += 1
        println(s" VAR : $count")
        putStrLn(s"stream: ${s.toString}")
    }
  } yield ()

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] =
    app
      .catchAllCause(c => putStrLn(c.toString))
//      .provideCustomLayer(env)
      .exitCode
}
