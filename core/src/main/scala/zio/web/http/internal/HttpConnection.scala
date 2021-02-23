package zio.web.http.internal

import java.io.{ IOException, StringReader }

import zio._
import zio.logging.{log, Logging}
import zio.nio.core.channels.{Selector, SelectionKey, SocketChannel}
import zio.nio.core.channels.SelectionKey.Operation
import zio.stream.ZStream
import zio.web.Endpoint
import zio.web.codec.JsonCodec
import zio.web.http.internal.{HttpLexer, HttpRouter}

private[http] class HttpConnection(channel: SocketChannel, closed: Promise[Throwable, Unit]) { self =>

  import HttpConnection.Data

  private val CR: Byte = 13 
  private val LF: Byte = 10
  private val CRLF: Chunk[Byte] = Chunk(CR, LF)

  val awaitOpen: UIO[Unit] = channel.isOpen.repeatUntil(identity).unit

  val awaitShutdown: IO[Throwable, Unit] = closed.await

  private def readLine(size: Int, prepend: Chunk[Byte] = Chunk.empty): IO[IOException, Data] = {
    // TODO: refactor using transducers
    // partitions the chunk into left (before seperator), middle (seperator) and right (after seperator)
    def partition(chunk: Chunk[Byte], acc: Chunk[Byte], buffer: Chunk[Byte])
      : (Chunk[Byte], Chunk[Byte], Chunk[Byte])
      = chunk.foldLeft((acc, buffer, (Chunk.empty: Chunk[Byte]))) {
        case ((left, sep, right), a) if sep.isEmpty =>
          if (a == CR) (left, sep :+ CR, right)
          else (left :+ a, sep, right)
        case ((left, sep, right), a) if sep.size == 1 =>
          if (a == LF) (left, sep :+ LF, right)
          else (left :+ CR :+ a, Chunk.empty, right)
        case ((left, sep, right), a) =>
          (left, sep, right :+ a)
      }
    
    def loop(acc: Chunk[Byte], buffer: Chunk[Byte]): IO[IOException, Data] =
      channel.readChunk(size).flatMap { chunk =>
        partition(chunk, acc, buffer) match {
          case (left, sep, right) => 
            if (sep.size == 2) ZIO.succeedNow(Data(left ++ sep, right))
            else loop(left, sep)
        }
      }

    partition(prepend, Chunk.empty, Chunk.empty) match {
      case (left, sep, _) if sep.isEmpty   => loop(left, Chunk.empty)
      case (left, sep, _) if sep.size == 1 => loop(left, sep)
      case (left, sep, right)              => ZIO.succeedNow(Data(left ++ sep, right))
    }
  }

  private def readHeaders(size: Int, prefix: Chunk[Byte]): IO[IOException, Data] = {
    def loop(acc: Chunk[Chunk[Byte]], prefix: Chunk[Byte]): IO[IOException, (Chunk[Chunk[Byte]], Chunk[Byte])] =
      readLine(size, prefix).flatMap {
        case Data(line, tail) =>
          if (line == CRLF) ZIO.succeedNow((acc :+ line, tail))
          else loop(acc :+ line, tail)
      }

    loop(Chunk.empty, prefix).map(v => Data(v._1.flatten, v._2))
  }

  val read: ZIO[Logging with HttpRouter, IOException, Unit] = 
    for {
      _         <- awaitOpen
      startLine <- readLine(32)
      _         <- log.info(s"Read start-line:\n${new String(startLine.value.toArray)}")
      (method, uri, version) = HttpLexer.parseStartLine(new StringReader(new String(startLine.value.toArray)))
      _         <- log.info(s"Parsed $method :: $uri :: $version")
      _ <- HttpRouter.route(method, uri, version).flatMap {
        case Some(endpoint) => 
          for {
            _       <- log.info(s"Request matched to [${endpoint.endpointName}].")
            headers <- readHeaders(32, startLine.tail)
            _       <- log.info(s"Read headers:\n${new String(headers.value.toArray)}")
            body    <- channel.readChunk(1024).map { headers.tail ++ _ }
            _       <- log.info(s"Read body:\n${new String(body.toArray)}")
            _      <- ZStream.fromChunk(body).transduce(JsonCodec.decoder(endpoint.request)).runHead.catchAll(_ => ZIO.none).flatMap {
              case Some(parsed) => 
                for {
                  _   <- log.info(s"Parsed body:\n${parsed}")
                  out <- endpoint.asInstanceOf[Endpoint.Api[ZEnv, Any, Any, Any]].handler(parsed).provideLayer(ZEnv.live)
                  _   <- log.info(s"Handler returned $out")
                } yield ()
              case None         => log.info(s"Parsing body failed")
            }
            
            // parse body if it's a POST request and pass it to the handler
            //response <- endpoint.handler(???)
          } yield ()
        case None => log.info("Request not matched.")
      }
      rep = uri.toString.stripPrefix("/") match {
        case name if name.nonEmpty => HttpConnection.HELLO(name.toString)
        case _                     => HttpConnection.HELLO_WORLD
      }
      res = Chunk.fromArray(rep.getBytes)
      _   <- channel.writeChunk(res)
      _   <- log.info(s"Sent response data")
      _   <- channel.close
    } yield ()

  val shutdown: URIO[Logging, Unit] = 
    for {
      _ <- log.debug("Stopping connection...")
      _ <- ZIO.whenM(channel.isOpen)(channel.close).unit.to(closed)
      _ <- log.debug("Connection stopped")
    } yield ()
}

private[http] object HttpConnection {

  case class Data(value: Chunk[Byte], tail: Chunk[Byte])

  val HELLO_WORLD =
    """HTTP/1.0 200 OK
      |Content-type: text/plain
      |
      |Hello, world!""".stripMargin

  def HELLO(name: String) =
    s"""HTTP/1.0 200 OK
       |Content-type: text/plain
       |
       |Hello, $name!""".stripMargin

  private def apply(channel: SocketChannel): ZManaged[Logging, IOException, HttpConnection] =
    (for {
      closed <- Promise.make[Throwable, Unit]
    } yield new HttpConnection(channel, closed)).toManaged(_.shutdown)

  private def register(channel: SocketChannel, selector: Selector)(connection: HttpConnection): ZManaged[Logging, IOException, SelectionKey] =
    (for {
      _   <- channel.configureBlocking(false)
      key <- channel.register(selector, Operation.Read, Some(connection))
    } yield key).toManaged(_.cancel)
      
  def spawnDaemon(channel: SocketChannel, selector: Selector): URIO[Logging, Unit] =
    HttpConnection(channel)
      .tap(register(channel, selector))
      .use(_.awaitShutdown)
      .forkDaemon
      .unit
}