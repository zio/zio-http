import zhttp.http._
import zhttp.service.Server
import zio._

/**
 * Example to read request content as a Stream
 */
object StreamingRequest extends App {

  val app  = HttpApp.collect { case _ -> Root / "health" =>
    Response.ok
  }
  val app1 = HttpApp.collectBuffered { case req @ _ -> Root / "upload" =>
    Response(status = Status.OK, content = req.content, headers = Header.transferEncodingChunked :: Nil)
  }

  override def run(args: List[String]): URIO[zio.ZEnv, ExitCode] = {

    // Starting the server (for more advanced startup configuration checkout `HelloWorldAdvanced`)
    Server.start(8090, (app +++ app1).silent).exitCode
  }
}
