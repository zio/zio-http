import zio._
import zhttp.service.Server
import zhttp.http.HttpApp
import zhttp.http._
import zio.stream.ZStream


/**
 * Example to encode content using a ZStream
 */
object DefaultHeaders extends App {
    val message = Chunk.fromArray("Hello world !\r\n".getBytes(HTTP_CHARSET))
private val app = HttpApp.collect {
    
      case Method.GET -> !! / "hello" =>
        Response.text("Hello from default headers").defaultHeaders

        // Chunk powered response
        case Method.GET -> !! / "chunked" =>
        Response(
                    status = Status.OK,
                    headers = List(Header.contentLength(message.length.toLong)),
                    data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
                  )
        // Chunk powered response with default headers
        case Method.GET -> !! / "chunked" =>
        Response(
                                      status = Status.OK,
                                      data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
                                    ).defaultHeaders
     // ZStream powered response
        case Method.GET -> !! / "stream1" =>
          Response(
            status = Status.OK,
            headers = List(Header.contentLength(message.length.toLong)),
            data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
          )
          // ZStream powered response with default headers
         case Method.GET -> !! / "stream2" =>
                    Response(
                      status = Status.OK,
                      data = HttpData.fromStream(ZStream.fromChunk(message)), // Encoding content using a ZStream
                    ).defaultHeaders

    }
  override def run(args: List[String]): URIO[ZEnv,ExitCode] = ???
  Server.start(8090, app.silent).exitCode
}
