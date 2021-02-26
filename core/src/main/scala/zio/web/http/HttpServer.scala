package zio.web.http

import java.io.IOException
import java.net.URI

import zio._
import zio.blocking.{ Blocking, blocking }
import zio.clock.Clock
import zio.duration._
import zio.logging.{ Logging, log }
import zio.nio.core.{ InetSocketAddress, SocketAddress }
import zio.nio.core.channels.{ SelectionKey, Selector, ServerSocketChannel }
import zio.nio.core.channels.SelectionKey.Operation
import zio.web.{ Endpoints, Handlers }
import zio.web.http.internal.{ HttpConnection, HttpController, HttpRouter }

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
    selector.select(500.millis).onInterrupt(selector.wakeup).flatMap {
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
              log.debug("Accepted connection") *> HttpConnection.spawnDaemon(router, controller, channel, selector)
            case None => log.debug("No connection is currently available to be accepted")
          }
    } yield ()

  private def read(key: SelectionKey): ZIO[Logging, IOException, Unit] =
    for {
      _ <- log.debug("Reading connection...")
      _ <- key.attachment.flatMap {
            case Some(attached) => attached.asInstanceOf[HttpConnection].read
            case None           => log.error("Connection is not ready to be read")
          }
    } yield ()

  private def write(key: SelectionKey): ZIO[Logging, IOException, Unit] =
    for {
      _ <- log.debug("Writing connection")
      _ = key
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
      router     <- HttpRouter.make(endpoints)
      controller <- HttpController.make(handlers, env)
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
}
