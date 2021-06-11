package zio.web.http

import java.io.{ IOException, StringReader }
import java.net.URI

import zio.{ Chunk, Has, IO, Promise, UIO, URIO, ZIO, ZManaged }
import zio.blocking.{ Blocking, blocking }
import zio.clock.Clock
import zio.duration._
import zio.logging.{ Logging, log }
import zio.nio.core.{ InetSocketAddress, SocketAddress }
import zio.nio.core.channels.{ SelectionKey, Selector, ServerSocketChannel, SocketChannel }
import zio.nio.core.channels.SelectionKey.Operation
import zio.stream.ZStream
import zio.web.{ AnyF, Endpoint, Endpoints, Handlers }
import zio.web.codec.JsonCodec
import zio.web.http.internal.{ ChannelReader, HttpController, HttpLexer, HttpRouter }

final class HttpServer private (
  router: HttpRouter,
  controller: HttpController[Any],
  selector: Selector,
  serverChannel: ServerSocketChannel,
  socketAddress: InetSocketAddress,
  closed: Promise[Throwable, Unit]
) {
  val awaitOpen: UIO[Unit] = serverChannel.isOpen.repeatUntil(identity).unit

  val awaitShutdown: IO[Throwable, Unit] = closed.await

  val shutdown: URIO[Logging, Unit] =
    for {
      _ <- log.info("Stopping server...")
      _ <- ZIO.whenM(serverChannel.isOpen)(serverChannel.close).unit.to(closed)
      _ <- log.info("Server stopped")
    } yield ()

  private val startup: ZIO[Blocking with Clock with Logging, IOException, Unit] =
    for {
      _       <- log.info("Starting server...")
      _       <- awaitOpen
      _       <- serverChannel.configureBlocking(false)
      ops     <- serverChannel.validOps
      _       <- serverChannel.register(selector, ops)
      address <- uri
      _       <- log.info(s"Server started at $address")
      _       <- run
    } yield ()

  val localAddress: UIO[InetSocketAddress] = awaitOpen.as(socketAddress)

  val uri: UIO[URI] =
    for {
      inet <- localAddress
      host <- inet.hostName
    } yield new URI("http", null, host, inet.port, "/", null, null)

  private val select: ZIO[Logging, IOException, Unit] =
    selector.select(10.millis).onInterrupt(selector.wakeup).flatMap {
      case 0 => ZIO.unit
      case _ =>
        selector.selectedKeys.flatMap { keys =>
          ZIO.foreachPar_(keys) { key =>
            key.readyOps.flatMap { ops =>
              for {
                _ <- ZIO.when(ops contains Operation.Accept)(accept)
                _ <- ZIO.when(ops contains Operation.Read)(read(key))
                _ <- ZIO.when(ops contains Operation.Write)(write(key))
                _ <- selector.removeKey(key)
              } yield ()
            }
          }
        }
    }

  private val accept: ZIO[Logging, IOException, Unit] =
    for {
      _ <- log.debug("Accepting connection...")
      _ <- serverChannel.accept.flatMap {
            case Some(channel) =>
              log.debug("Accepted connection") *> HttpServer.Connection(router, controller, channel, selector)
            case None => log.debug("No connection is currently available to be accepted")
          }
    } yield ()

  private def read(key: SelectionKey): ZIO[Logging, IOException, Unit] =
    for {
      _ <- log.debug("Reading connection...")
      _ <- key.attachment.flatMap {
            case Some(attached) => attached.asInstanceOf[HttpServer.Connection].read
            case None           => log.error("Connection is not ready to be read")
          }
    } yield ()

  private def write(key: SelectionKey): ZIO[Logging, IOException, Unit] =
    for {
      _ <- log.debug("Writing connection")
      _ <- key.attachment.flatMap {
            case Some(attached) => attached.asInstanceOf[HttpServer.Connection].write
            case None           => log.error("Connection is not ready to be written")
          }
    } yield ()

  private val run: ZIO[Blocking with Clock with Logging, IOException, Nothing] =
    (select *> ZIO.yieldNow).forever
      .onInterrupt(log.debug("Selector loop interrupted"))
}

object HttpServer {

  val run: ZIO[Has[HttpServer] with Blocking with Clock with Logging, IOException, HttpServer] =
    ZIO.service[HttpServer].tap(_.startup.orDie)

  def build[M[+_], R, Ids](
    config: HttpServerConfig,
    endpoints: Endpoints[M, Ids],
    handlers: Handlers[M, R, Ids],
    env: R
  ): ZManaged[Blocking with Logging, IOException, HttpServer] =
    for {
      closed     <- Promise.make[Throwable, Unit].toManaged_
      address    <- SocketAddress.inetSocketAddress(config.host, config.port).toManaged_
      channel    <- openChannel(address, 0)
      selector   <- Selector.make.toManaged(_.close.tapCause(cause => log.error("Closing selector failed", cause)).orDie)
      router     <- HttpRouter.make[M](endpoints)
      controller <- HttpController.make[M, R, Ids](handlers, env)
    } yield new HttpServer(
      router,
      controller.asInstanceOf[HttpController[Any]],
      selector,
      channel,
      address,
      closed
    )

  private def openChannel(
    address: InetSocketAddress,
    maxPending: Int
  ): ZManaged[Blocking, IOException, ServerSocketChannel] =
    ServerSocketChannel.open
      .tap(_.configureBlocking(false))
      .toManaged(_.close.orDie)
      .tapM(channel => blocking { channel.bind(address, maxPending) })

  private[HttpServer] class Connection private (
    router: HttpRouter,
    controller: HttpController[Any],
    selector: Selector,
    channel: SocketChannel,
    response: Promise[IOException, Chunk[Byte]],
    closed: Promise[Throwable, Unit]
  ) { self =>

    lazy val awaitOpen: UIO[Unit] = channel.isOpen.repeatUntil(identity).unit

    lazy val awaitShutdown: IO[Throwable, Unit] = closed.await

    lazy val read: ZIO[Logging, Nothing, Unit] =
      (for {
        reader    <- awaitOpen.as(ChannelReader(channel, 32))
        data      <- reader.readUntilNewLine()
        firstLine = new String(data.value.toArray)
        _         <- log.info(s"Read start line:\n$firstLine")
        startLine = HttpLexer.parseStartLine(new StringReader(firstLine))
        _         <- log.info(s"Parsed ${startLine}")
        _ <- router.route(startLine).flatMap {
              case None =>
                for {
                  _ <- log.info("Request not matched")
                  _ <- Connection.notFoundResponse().to(response)
                } yield ()
              case Some(endpoint) =>
                for {
                  _           <- log.info(s"Request matched to [${endpoint.endpointName}].")
                  data        <- reader.readUntilEmptyLine(data.tail)
                  headerLines = new String(data.value.toArray)
                  _           <- log.info(s"Read headers:\n$headerLines")
                  // TODO: extract headers parsing and wrap them in HttpHeaders to tidy up a little here
                  headerNames  = Array("Content-Length") // TODO: handle when not given then do not read the content
                  headerValues = HttpLexer.parseHeaders(headerNames, new StringReader(headerLines))
                  headers = headerNames
                    .zip(headerValues.map(_.headOption))
                    .collect { case (key, Some(value)) => key -> value }
                    .toMap
                  _          <- log.info(s"Parsed headers:\n$headers")
                  bodyLength = headers("Content-Length").toInt
                  data       <- reader.read(bodyLength, data.tail)
                  bodyLines  = new String(data.toArray)
                  _          <- log.info(s"Read body:\n$bodyLines")
                  input      <- Connection.decodeInput(data, endpoint)
                  _          <- log.info(s"Parsed body:\n$input")
                  output     <- controller.handle(endpoint)(input, ())
                  _          <- log.info(s"Handler returned $output")
                  _          <- Connection.sucessResponse(output, endpoint).to(response)
                } yield ()
            }
      } yield ())
        .catchAllCause(
          cause =>
            for {
              _ <- log.error("Internal server error occured", cause)
              _ <- Connection.internalServerErrorResponse().to(response)
            } yield ()
        )
        .ensuring(channel.register(selector, Operation.Write, Some(self)).orElse(shutdown))

    lazy val write: ZIO[Logging, IOException, Unit] =
      (for {
        bytes <- response.await
        _     <- channel.writeChunk(bytes)
        _     <- log.info(s"Sent response data")
      } yield ()).ensuring(shutdown)

    lazy val shutdown: URIO[Logging, Unit] =
      for {
        _ <- log.debug("Stopping connection...")
        _ <- ZIO.whenM(channel.isOpen)(channel.close).unit.to(closed)
        _ <- log.debug("Connection stopped")
      } yield ()
  }

  private[HttpServer] object Connection {

    def apply[R](
      router: HttpRouter,
      controller: HttpController[Any],
      channel: SocketChannel,
      selector: Selector
    ): URIO[Logging, Unit] =
      (for {
        response <- Promise.make[IOException, Chunk[Byte]]
        closed   <- Promise.make[Throwable, Unit]
      } yield new Connection(router, controller, selector, channel, response, closed))
        .toManaged(_.shutdown)
        .tap(register(channel, selector))
        .use(_.awaitShutdown)
        .forkDaemon
        .unit

    def register(channel: SocketChannel, selector: Selector)(
      connection: Connection
    ): ZManaged[Logging, IOException, SelectionKey] =
      (for {
        _   <- channel.configureBlocking(false)
        key <- channel.register(selector, Operation.Read, Some(connection))
      } yield key).toManaged(_.cancel)

    def notFoundResponse(): UIO[Chunk[Byte]] =
      ZIO.succeed(httpHeaders("HTTP/1.0 404 Not Found"))

    def internalServerErrorResponse(): UIO[Chunk[Byte]] =
      ZIO.succeed(httpHeaders("HTTP/1.0 500 Internal Server Error"))

    def sucessResponse[O](output: O, endpoint: Endpoint[AnyF, _, _, _]): UIO[Chunk[Byte]] =
      for {
        body <- encodeOutput(output, endpoint.asInstanceOf[Endpoint[AnyF, _, _, O]])
        headers = httpHeaders(
          "HTTP/1.0 200 OK",
          "Content-Type: text/plain",
          s"Content-Length: ${body.size}"
        )
      } yield headers ++ body

    // TODO: make codec configurable
    def decodeInput[I](bytes: Chunk[Byte], endpoint: Endpoint[AnyF, _, I, _]): IO[IOException, I] =
      ZStream
        .fromChunk(bytes)
        .transduce(JsonCodec.decoder(endpoint.request))
        .runHead
        .catchAll(_ => ZIO.none)
        .someOrElseM(ZIO.fail(new IOException("Could not decode input")))

    def encodeOutput[O](output: O, endpoint: Endpoint[AnyF, _, _, O]): UIO[Chunk[Byte]] =
      ZStream(output)
        .transduce(JsonCodec.encoder(endpoint.response))
        .runCollect

    def httpHeaders(headers: String*): Chunk[Byte] =
      Chunk.fromArray(
        headers
          .mkString("", "\r\n", "\r\n\r\n")
          .getBytes
      )
  }
}
