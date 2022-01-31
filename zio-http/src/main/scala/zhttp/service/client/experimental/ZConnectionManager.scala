package zhttp.service.client.experimental

import io.netty.bootstrap.Bootstrap
import io.netty.channel.{Channel, ChannelFuture, ChannelFutureListener}
import io.netty.handler.codec.http.FullHttpRequest
import zhttp.http.URL
import zhttp.service.client.ClientSSLHandler.ClientSSLOptions
import zio.{Promise, Ref, Task, ZIO}

import java.net.InetSocketAddress
import scala.collection.mutable

case class ZConnectionManager(
  connRef: Ref[mutable.Map[InetSocketAddress, Channel]],
  currentExecRef: mutable.Map[Channel, Promise[Throwable, Resp]] = mutable.Map.empty[Channel, Promise[Throwable, Resp]],
  boo: Bootstrap,
  zExec: zhttp.service.HttpRuntime[Any],
) {
  def fetchConnection(jReq: FullHttpRequest): Task[Channel] = {
    for {
      mp  <- connRef.get
      _   <- ZIO.effect(println(s"CONNECTION MAP : $mp"))
      url <- ZIO.fromEither(URL.fromString(jReq.uri()))
      host = url.host
      port = url.port.getOrElse(80) match {
        case -1   => 80
        case port => port
      }
      inetSockAddress <- host match {
        case Some(h) => Task.succeed(new InetSocketAddress(h, port))
        case _       => Task.fail(new Exception("error getting host"))
      }
      conn <- mp.get(inetSockAddress) match {
        case Some(c) =>
          println(s"CONN FOUND REUSING IT: $c  is writable: ${c.isWritable} is active: ${c.isActive}")
          if (c.isWritable)
            Task.succeed(c)
          else
            buildChannel(jReq, "http", inetSockAddress)

        case _ =>
          println(s"CONN NOT FOUND CREATING NEW")
          buildChannel(jReq, "http", inetSockAddress)
      }
      _ <- connRef.update { m =>
        m += (inetSockAddress -> conn)
        println(s"NEW M: $m")
        println(s"CHANNEL MAP SIZE: ${m.size}")
        m
      }
    } yield conn

  }

  def buildChannel[R](jReq: FullHttpRequest, scheme: String, inetSocketAddress: InetSocketAddress): Task[Channel] = {
    for {
      //      promise <- Promise.make[Throwable, Resp]
      _ <- ZIO.effect(println(s""))

      init = ZClientChannelInitializer(
        ZClientInboundHandler(zExec, jReq, this),
        scheme,
        ClientSSLOptions.DefaultSSL,
      )

      _ <- ZIO.effect(println(s"for ${jReq.uri()} CONNECTING to ${inetSocketAddress}"))
      (h, p) = (inetSocketAddress.toString.split("/")(0), inetSocketAddress.toString.split(":")(1))
      _ <- ZIO.effect(println(s"for ${jReq.uri()} CONNECTING to ${(h, p)}"))
      chf = boo.handler(init).connect(h, p.toInt)
      _ <- ZIO
        .effect(
          chf.addListener(new ChannelFutureListener() {
            override def operationComplete(future: ChannelFuture): Unit = {
              val channel = future.channel()
              println(s"CONNECTED USING CHH ID: ${channel.id()}")
              if (!future.isSuccess()) {
                println(s"error: ${future.cause().getMessage}")
                future.cause().printStackTrace()
              } else {
                println("FUTURE SUCCESS");
              }
            }
          }): Unit,
        )
    } yield chf.channel()
  }

}

object ZConnectionManager {}
