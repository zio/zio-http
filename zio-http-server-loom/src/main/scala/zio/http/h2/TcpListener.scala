package zio.http.h2

import java.io.{ByteArrayInputStream, InputStream, OutputStream}
import java.net.InetSocketAddress
import java.nio.channels.{Channels, ServerSocketChannel, SocketChannel}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.security.KeyStore
import java.security.cert.{Certificate, CertificateFactory, X509Certificate}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey, SecureRandom}
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import javax.net.ssl.{KeyManagerFactory, SSLContext, SSLHandshakeException, SSLParameters, SSLSocket}

import scala.jdk.CollectionConverters._
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import zio.blocks.config.Secret
import zio.http.{TlsConfig, TlsSource}

class TcpListener(
  host: String,
  port: Int,
  tls: Option[TlsConfig],
  connectionHandler: (InputStream, OutputStream) => Unit,
) {
  def start(): BoundListener = {
    val serverChannel     = ServerSocketChannel.open()
    val running           = new AtomicBoolean(true)
    val connectionCounter = new AtomicLong(0L)
    val activeConnections = ConcurrentHashMap.newKeySet[AutoCloseable]()
    val sslContext        = tls.map(TcpListener.createSslContext)

    serverChannel.bind(new InetSocketAddress(host, port))

    val acceptor = Thread
      .ofVirtual()
      .name(s"zio-http-h2-$host:$port")
      .start(() => acceptLoop(serverChannel, running, activeConnections, connectionCounter, sslContext))

    val localAddress = serverChannel.getLocalAddress.asInstanceOf[InetSocketAddress]

    BoundListener(
      localAddress.getHostString,
      localAddress.getPort,
      () => {
        if (running.compareAndSet(true, false)) {
          closeQuietly(serverChannel)
          closeAll(activeConnections)
          acceptor.interrupt()
          acceptor.join()
        }
      },
      () => running.get() && acceptor.isAlive && serverChannel.isOpen,
    )
  }

  private def acceptLoop(
    serverChannel: ServerSocketChannel,
    running: AtomicBoolean,
    activeConnections: java.util.Set[AutoCloseable],
    connectionCounter: AtomicLong,
    sslContext: Option[SSLContext],
  ): Unit = {
    while (running.get() && serverChannel.isOpen) {
      try {
        val channel      = serverChannel.accept()
        val connectionId = connectionCounter.incrementAndGet()
        Thread
          .ofVirtual()
          .name(s"zio-http-conn-$connectionId")
          .start(() => handleConnection(channel, activeConnections, sslContext))
      } catch {
        case _: java.nio.channels.AsynchronousCloseException if !running.get() || !serverChannel.isOpen => ()
        case _: java.net.SocketException if !running.get() || !serverChannel.isOpen                     => ()
      }
    }
  }

  private def handleConnection(
    channel: SocketChannel,
    activeConnections: java.util.Set[AutoCloseable],
    sslContext: Option[SSLContext],
  ): Unit = {
    sslContext match {
      case Some(context) =>
        val sslSocket = TcpListener.createTlsSocket(context, channel)
        activeConnections.add(sslSocket)
        try {
          connectionHandler(sslSocket.getInputStream, sslSocket.getOutputStream)
        } finally {
          activeConnections.remove(sslSocket)
          closeQuietly(sslSocket)
        }
      case None          =>
        activeConnections.add(channel)
        try {
          connectionHandler(Channels.newInputStream(channel), Channels.newOutputStream(channel))
        } finally {
          activeConnections.remove(channel)
          closeQuietly(channel)
        }
    }
  }

  private def closeAll(resources: java.util.Set[AutoCloseable]): Unit =
    resources.asScala.foreach(closeQuietly)

  private def closeQuietly(resource: AutoCloseable): Unit =
    if (resource != null) {
      try resource.close()
      catch {
        case NonFatal(_) => ()
      }
    }
}

case class BoundListener(
  host: String,
  port: Int,
  close: () => Unit,
  isRunning: () => Boolean,
)

private object TcpListener {
  private val PrivateKeyHeader = "-----BEGIN PRIVATE KEY-----"
  private val PrivateKeyFooter = "-----END PRIVATE KEY-----"
  private val RsaKeyHeader     = "-----BEGIN RSA PRIVATE KEY-----"
  private val RsaKeyFooter     = "-----END RSA PRIVATE KEY-----"

  def createSslContext(tls: TlsConfig): SSLContext =
    firstSslContext(tls).getOrElse {
      val certificates = loadCertificates(tls.certChain)
      val privateKey   = loadPrivateKey(tls.privateKey)
      val keyStore     = KeyStore.getInstance(KeyStore.getDefaultType)
      val password     = Array.emptyCharArray

      keyStore.load(null, password)
      keyStore.setKeyEntry("zio-http-server-loom", privateKey, password, certificates.map(identity[Certificate]))

      val keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm)
      keyManagerFactory.init(keyStore, password)

      val sslContext = SSLContext.getInstance("TLS")
      sslContext.init(keyManagerFactory.getKeyManagers, null, new SecureRandom())
      sslContext
    }

  def createTlsSocket(sslContext: SSLContext, channel: SocketChannel): SSLSocket = {
    val socket = sslContext.getSocketFactory
      .createSocket(channel.socket(), channel.socket().getInetAddress.getHostAddress, channel.socket().getPort, true)
      .asInstanceOf[SSLSocket]

    val sslEngine  = sslContext.createSSLEngine()
    val parameters = withH2Alpn(sslEngine.getSSLParameters)

    sslEngine.setUseClientMode(false)
    sslEngine.setSSLParameters(parameters)

    socket.setUseClientMode(false)
    socket.setSSLParameters(parameters)
    socket.startHandshake()

    val negotiatedProtocol = socket.getApplicationProtocol
    if (negotiatedProtocol != "h2") {
      closeQuietly(socket)
      throw new SSLHandshakeException(s"Expected ALPN protocol 'h2' but negotiated '$negotiatedProtocol'")
    }

    socket
  }

  private def withH2Alpn(parameters: SSLParameters): SSLParameters = {
    parameters.setApplicationProtocols(Array("h2"))
    parameters
  }

  private def firstSslContext(tls: TlsConfig): Option[SSLContext] =
    tls.certChain match {
      case TlsSource.SslContext(ctx) => Some(ctx)
      case _                         =>
        tls.privateKey match {
          case TlsSource.SslContext(ctx) => Some(ctx)
          case _                         => None
        }
    }

  private def loadCertificates(source: TlsSource): Array[X509Certificate] = {
    val bytes        = readSourceBytes(source)
    val certificates =
      CertificateFactory.getInstance("X.509").generateCertificates(new ByteArrayInputStream(bytes)).asScala

    certificates.collect { case certificate: X509Certificate => certificate }.toArray
  }

  private def loadPrivateKey(source: TlsSource): PrivateKey = {
    val pem = readSourceString(source)

    parsePemBlock(pem, PrivateKeyHeader, PrivateKeyFooter) match {
      case Some(pkcs8Bytes) => parsePkcs8PrivateKey(pkcs8Bytes)
      case None             =>
        parsePemBlock(pem, RsaKeyHeader, RsaKeyFooter) match {
          case Some(pkcs1Bytes) => parsePkcs8PrivateKey(wrapPkcs1RsaKey(pkcs1Bytes))
          case None             => throw new IllegalArgumentException("Unsupported private key PEM format")
        }
    }
  }

  private def parsePkcs8PrivateKey(keyBytes: Array[Byte]): PrivateKey = {
    val spec = new PKCS8EncodedKeySpec(keyBytes)

    List("RSA", "EC", "DSA", "Ed25519", "Ed448").iterator
      .map(algorithm => Try(KeyFactory.getInstance(algorithm).generatePrivate(spec)))
      .collectFirst { case Success(privateKey) => privateKey }
      .getOrElse(throw new IllegalArgumentException("Unable to parse private key with supported algorithms"))
  }

  private def readSourceBytes(source: TlsSource): Array[Byte] =
    source match {
      case TlsSource.FilePath(path)    => Files.readAllBytes(path)
      case TlsSource.PemString(secret) => Secret.unwrap(secret).getBytes(StandardCharsets.UTF_8)
      case TlsSource.SslContext(_) => throw new IllegalArgumentException("SSLContext source does not expose PEM bytes")
    }

  private def readSourceString(source: TlsSource): String =
    new String(readSourceBytes(source), StandardCharsets.UTF_8)

  private def parsePemBlock(pem: String, header: String, footer: String): Option[Array[Byte]] = {
    val start = pem.indexOf(header)
    val end   = pem.indexOf(footer)

    if (start < 0 || end < 0 || end <= start) None
    else {
      val base64 = pem
        .substring(start + header.length, end)
        .replaceAll("\\s", "")

      Some(Base64.getDecoder.decode(base64))
    }
  }

  private def wrapPkcs1RsaKey(pkcs1Bytes: Array[Byte]): Array[Byte] = {
    val rsaAlgorithmIdentifier = Array[Byte](
      0x30.toByte,
      0x0d.toByte,
      0x06.toByte,
      0x09.toByte,
      0x2a.toByte,
      0x86.toByte,
      0x48.toByte,
      0x86.toByte,
      0xf7.toByte,
      0x0d.toByte,
      0x01.toByte,
      0x01.toByte,
      0x01.toByte,
      0x05.toByte,
      0x00.toByte,
    )
    val version                = Array[Byte](0x02.toByte, 0x01.toByte, 0x00.toByte)
    val privateKeyOctetString  = derEncode(0x04, pkcs1Bytes)
    val privateKeyInfoContent  = version ++ rsaAlgorithmIdentifier ++ privateKeyOctetString

    derEncode(0x30, privateKeyInfoContent)
  }

  private def derEncode(tag: Int, content: Array[Byte]): Array[Byte] =
    Array(tag.toByte) ++ derLength(content.length) ++ content

  private def derLength(length: Int): Array[Byte] =
    if (length < 128) Array(length.toByte)
    else {
      val bytes = BigInt(length).toByteArray.dropWhile(_ == 0.toByte)
      Array((0x80 | bytes.length).toByte) ++ bytes
    }

  private def closeQuietly(resource: AutoCloseable): Unit =
    if (resource != null) {
      try resource.close()
      catch {
        case NonFatal(_) => ()
      }
    }
}
