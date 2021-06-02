package zhttp.service.client

import java.io.FileInputStream
import java.security.KeyStore
import io.netty.channel.ChannelPipeline
import io.netty.handler.codec.http.{HttpClientCodec => JHttpClientCodec}
import io.netty.handler.ssl.SslContext
import javax.net.ssl.TrustManagerFactory
import zhttp.core.{JChannel, JChannelHandler, JChannelInitializer, JHttpObjectAggregator}

import scala.util.{Failure, Success, Try}
final case class ClientChannelInitializer[R](channelHandler: JChannelHandler, port: Int)
    extends JChannelInitializer[JChannel]() {
  override def initChannel(ch: JChannel): Unit = {
    import io.netty.handler.ssl.SslContextBuilder
    // truststore
    val trustStore: Option[KeyStore]       = Option(KeyStore.getInstance("JKS"))
    val trustStorePath: Option[String]     = Option(System.getProperty("javax.net.ssl.trustStore"))
    val trustStorePassword: Option[String] = Option(System.getProperty("javax.net.ssl.trustStorePassword"))
    val trustManagerFactory                = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())

    val file: Try[FileInputStream] = Try(new FileInputStream(trustStorePath match {
      case Some(value) => value
      case None        => throw new Exception
    }))

    val ssl: SslContext            = trustStore match {
      case Some(ts) =>
        Try(
          ts.load(
            file match {
              case Failure(exception) => throw exception
              case Success(value)     => value
            },
            trustStorePassword match {
              case Some(value) => value.toCharArray
              case None        => throw new Exception
            },
          ),
        ).toEither match {
          case Left(_)  => SslContextBuilder.forClient().build()
          case Right(_) => {
            trustManagerFactory.init(ts)
            SslContextBuilder.forClient().trustManager(trustManagerFactory).build()
          }
        }

      case None => SslContextBuilder.forClient().build()
    }
    val sslCtx: Option[SslContext] = if (port == 443) Some(ssl) else None
    val p: ChannelPipeline         = ch
      .pipeline()
      .addLast(new JHttpClientCodec)
      .addLast(new JHttpObjectAggregator(Int.MaxValue))
      .addLast(channelHandler)
    sslCtx match {
      case Some(value) => p.addFirst(value.newHandler(ch.alloc))
      case None        => ()
    }
    ()
  }
}
