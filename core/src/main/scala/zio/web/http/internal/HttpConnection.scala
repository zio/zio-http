package zio.web.http.internal

import java.io.{ IOException, StringReader }

import zio._
import zio.logging.{ Logging, log }
import zio.nio.core.channels.{ SelectionKey, Selector, SocketChannel }
import zio.nio.core.channels.SelectionKey.Operation
import zio.stream.ZStream
import zio.web.{ AnyF, Endpoint }
import zio.web.codec.JsonCodec
import zio.web.http.internal.{ HttpController, HttpLexer, HttpRouter }

final private[http] class HttpConnection(
  router: HttpRouter,
  controller: HttpController[Any],
  channel: SocketChannel,
  closed: Promise[Throwable, Unit]
) { self =>

  val awaitOpen: UIO[Unit] = channel.isOpen.repeatUntil(identity).unit

  val awaitShutdown: IO[Throwable, Unit] = closed.await

  val read: ZIO[Logging, IOException, Unit] =
    (for {
      _         <- awaitOpen
      firstLine <- HttpConnection.readLine(channel, 32)
      _         <- log.info(s"Read first-line:\n${new String(firstLine.value.toArray)}")
      startLine = HttpLexer.parseStartLine(new StringReader(new String(firstLine.value.toArray)))
      _         <- log.info(s"Parsed ${startLine}")
      endpoint  <- router.route(startLine)
      _         <- log.info(s"Request matched to [${endpoint.endpointName}].")
      headers   <- HttpConnection.readHeaders(channel, 32, firstLine.tail)
      _         <- log.info(s"Read headers:\n${new String(headers.value.toArray)}")
      body      <- channel.readChunk(1024).map { headers.tail ++ _ }
      _         <- log.info(s"Read body:\n${new String(body.toArray)}")
      input     <- HttpConnection.decodeInput(body, endpoint)
      _         <- log.info(s"Parsed body:\n${input}")
      output    <- controller.handle(endpoint)(input, ())
      _         <- log.info(s"Handler returned $output")
      response  = HttpConnection.sucessResponse(output)
      _         <- channel.writeChunk(response)
      _         <- log.info(s"Sent response data")
      _         <- channel.close
    } yield ()).tapError(e => log.error(s"Failed due to ${e.getMessage}"))

  val shutdown: URIO[Logging, Unit] =
    for {
      _ <- log.debug("Stopping connection...")
      _ <- ZIO.whenM(channel.isOpen)(channel.close).unit.to(closed)
      _ <- log.debug("Connection stopped")
    } yield ()
}

private[http] object HttpConnection {

  case class Data(value: Chunk[Byte], tail: Chunk[Byte])

  def sucessResponse(content: Any): Chunk[Byte] =
    Chunk.fromArray(
      s"""HTTP/1.0 200 OK
         |Content-type: text/plain
         |
         |${content.toString}""".stripMargin.getBytes
    )

  private def apply(
    router: HttpRouter,
    controller: HttpController[Any],
    channel: SocketChannel
  ): ZManaged[Logging, IOException, HttpConnection] =
    (for {
      closed <- Promise.make[Throwable, Unit]
    } yield new HttpConnection(router, controller, channel, closed)).toManaged(_.shutdown)

  private def register(channel: SocketChannel, selector: Selector)(
    connection: HttpConnection
  ): ZManaged[Logging, IOException, SelectionKey] =
    (for {
      _   <- channel.configureBlocking(false)
      key <- channel.register(selector, Operation.Read, Some(connection))
    } yield key).toManaged(_.cancel)

  def spawnDaemon[R](
    router: HttpRouter,
    controller: HttpController[Any],
    channel: SocketChannel,
    selector: Selector
  ): URIO[Logging, Unit] =
    HttpConnection(router, controller, channel)
      .tap(register(channel, selector))
      .use(_.awaitShutdown)
      .forkDaemon
      .unit

  // TODO: make codec configurable
  private def decodeInput[I](bytes: Chunk[Byte], endpoint: Endpoint[AnyF, _, I, _]): IO[IOException, I] =
    ZStream
      .fromChunk(bytes)
      .transduce(JsonCodec.decoder(endpoint.request))
      .runHead
      .catchAll(_ => ZIO.none)
      .someOrElseM(ZIO.fail(new IOException("Could not decode input")))

  /**
   * We don't want to eagerly parse the whole request. These util methods allow to gradually extract:
   * - the start line (enough for basic route exists check)
   * - header lines (extract additional info eg. Auth Bearer token)
   * - message body (only parsed if a matching route was found)
   *
   * TODO: refactor to transducers?
   */
  private val CR: Byte          = 13
  private val LF: Byte          = 10
  private val CRLF: Chunk[Byte] = Chunk(CR, LF)

  private def readLine(channel: SocketChannel, size: Int, prepend: Chunk[Byte] = Chunk.empty): IO[IOException, Data] = {

    def partition(chunk: Chunk[Byte], acc: Chunk[Byte], buffer: Chunk[Byte]): (Chunk[Byte], Chunk[Byte], Chunk[Byte]) =
      chunk.foldLeft((acc, buffer, (Chunk.empty: Chunk[Byte]))) {
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
      channel
        .readChunk(size)
        .flatMap { chunk =>
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

  private def readHeaders(channel: SocketChannel, size: Int, prefix: Chunk[Byte]): IO[IOException, Data] = {
    def loop(acc: Chunk[Chunk[Byte]], prefix: Chunk[Byte]): IO[IOException, (Chunk[Chunk[Byte]], Chunk[Byte])] =
      readLine(channel, size, prefix).flatMap {
        case Data(line, tail) =>
          if (line == CRLF) ZIO.succeedNow((acc :+ line, tail))
          else loop(acc :+ line, tail)
      }

    loop(Chunk.empty, prefix).map(v => Data(v._1.flatten, v._2))
  }
}
