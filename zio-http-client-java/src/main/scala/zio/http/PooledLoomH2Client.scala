package zio.http

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLSocket

import scala.annotation.experimental
import scala.collection.mutable.ListBuffer
import scala.util.control.NonFatal

/**
 * Pooled Loom (virtual-thread, blocking) HTTP/2 client over the T14 framing.
 *
 * Each authority (scheme/host/port) gets at most `pool.maxPerHost` connections
 * (and `pool.maxTotal` globally); every connection carries sequential exchanges
 * with lazily-streamed response bodies. Pool mechanics, honored exactly per the
 * [[PoolConfig]] idle-reclamation contract:
 *   - idle longer than `pool.idleTimeout` -> reclaimed on next checkout (never
 *     handed out without this check plus a socket-liveness check);
 *   - at most `pool.queueSize` borrowers wait for a connection; the
 *     (`queueSize` + 1)-th fails fast with an [[IOException]] naming the pool
 *     (no unbounded queue, no fail-open);
 *   - queued borrowers wait at most `effectiveConnectTimeout`, then fail with a
 *     [[TimeoutException]] naming the acquire (bounded wait, never a hang);
 *     queue parking uses fair semaphores (no busy spin - jvm-perf);
 *   - every deadline is read through `ClientConfig.effective*`: connect
 *     (acquire + TCP connect), request (response HEADERS), stream (lazy body
 *     reads via socket `SoTimeout`). Socket expiries map to
 *     [[TimeoutException]], never a bare [[java.net.SocketTimeoutException]];
 *   - in-flight cancel sends RST_STREAM (`H2Error.Code.CANCEL`) then closes the
 *     socket; the connection is evicted, never repooled;
 *   - server GOAWAY / server-side close / timeouts poison the connection: it is
 *     evicted on first observation and never handed out again (no black-hole
 *     reuse). Failures before the request HEADERS flush retry once on a fresh
 *     connection (the server never saw the request); anything later propagates -
 *     no retry policy beyond pool mechanics.
 *
 * Asymmetry (deliberate): request upload stays fully buffered (`Body.toArray`,
 * T14 semantics); response download streams frame-by-frame through a
 * lazily-pulled [[Body]] (one 16 KiB carry buffer live at a time). The pool
 * slot stays checked out until the lazy body is fully consumed (or the exchange
 * is cancelled / fails): abandoning a body pins its connection until the socket
 * deadline fires, so always consume or cancel.
 *
 * TLS mirrors [[LoomH2ClientDriver]]: `h2` negotiates into the pool; an
 * `http/1.1` outcome delegates one-shot to the JDK h1.1 leg under
 * [[ClientAlpnPolicy.H2PreferredWithH11Fallback]] (pool slots released first)
 * and fails fast with [[SSLHandshakeException]] under strict policies. The
 * pooled leg itself is H2-only.
 *
 * No internal threads: reclamation is lazy on checkout, parking is semaphore
 * based, and [[CancellableExchange]] runs on the caller's thread. `close()` is
 * idempotent; it releases idle connections, fails future checkouts with
 * [[IllegalStateException]], and leaves in-flight exchanges to their own
 * deadlines.
 */
@experimental
final class PooledLoomH2Client private (
  config: ClientConfig,
  sslContextOverride: Option[SSLContext],
) extends Client {

  // Fail fast (MINOR-4): structured TLS material is loaded and parsed here so
  // a bad path/key/password throws at construction. Skipped when a
  // programmatic SSLContext bypasses structured config entirely.
  if (sslContextOverride.isEmpty) ClientTlsSupport.validateTlsMaterial(config.tls)

  private val poolConfig = config.pool

  private val lock   = new Object
  private var closed = false
  private val idle   = scala.collection.mutable.Map.empty[PoolKey, ListBuffer[PooledH2Connection]]

  private val globalPermits = new Semaphore(poolConfig.maxTotal, true)
  private val hostPermits   = new ConcurrentHashMap[PoolKey, Semaphore]()

  private val checkedOut = new AtomicInteger(0)
  private val queued     = new AtomicInteger(0)

  def stats: PooledLoomH2Client.Stats =
    lock.synchronized {
      PooledLoomH2Client.Stats(checkedOut.get(), idle.values.map(_.size).sum, queued.get())
    }

  def isClosed: Boolean =
    lock.synchronized(closed)

  def close(): Unit = {
    val drained = lock.synchronized {
      if (closed) Nil
      else {
        closed = true
        val all = idle.values.flatten.toList
        idle.clear()
        all
      }
    }
    drained.foreach(_.close())
  }

  def send(request: Request): Response =
    sendCancellable(request).await()

  /**
   * Starts an exchange, acquiring its pool slot immediately.
   * [[CancellableExchange.await]] blocks the calling (virtual) thread for the
   * response HEADERS; the body - unless empty - streams lazily and holds the
   * slot until fully consumed.
   */
  def sendCancellable(request: Request): CancellableExchange = {
    val (key, _, _, _) = route(request)
    new CancellableExchange(key, request)
  }

  private def route(request: Request): (PoolKey, String, Int, String) = {
    val url    = request.url
    if (!url.isAbsolute) throw new IllegalArgumentException("PooledLoomH2Client requires absolute request URLs")
    val host   = url.host.getOrElse(
      throw new IllegalArgumentException("PooledLoomH2Client requires an absolute URL with a host"),
    )
    val scheme = url.scheme
      .map(_.text)
      .getOrElse(
        throw new IllegalArgumentException("PooledLoomH2Client requires a URL with a scheme"),
      )
    if (scheme != "http" && scheme != "https")
      throw new IllegalArgumentException(s"PooledLoomH2Client supports http/https only, got '$scheme'")
    val port   = url.port.getOrElse(if (scheme == "http") 80 else 443)
    (PoolKey(scheme, host, port), host, port, scheme)
  }

  private def permitsFor(key: PoolKey): Semaphore =
    hostPermits.computeIfAbsent(key, _ => new Semaphore(poolConfig.maxPerHost, true))

  private def acquireTimeoutMs(): Long =
    math.max(1L, config.effectiveConnectTimeout.toMillis)

  /**
   * Acquires a slot pair (global + per-host) and a live connection. Fail-fast
   * when the queue overflows; bounded wait with [[TimeoutException]] after;
   * [[InterruptedException]] propagates untouched (cancel-during-queue).
   */
  @throws[InterruptedException]
  private def checkout(key: PoolKey): Checkout = {
    if (isClosed) throw new IllegalStateException("PooledLoomH2Client is closed")
    if (queued.incrementAndGet() > poolConfig.queueSize) {
      queued.decrementAndGet()
      throw new IOException(
        s"Connection pool exhausted for $key: ${poolConfig.queueSize} borrowers already queued " +
          s"(maxPerHost=${poolConfig.maxPerHost}, maxTotal=${poolConfig.maxTotal})",
      )
    }
    try {
      val deadlineNanos = java.lang.System.nanoTime() + acquireTimeoutMs() * 1000000L
      if (!globalPermits.tryAcquire(deadlineNanos - java.lang.System.nanoTime(), TimeUnit.NANOSECONDS))
        throw new TimeoutException(
          s"Timed out acquiring a pooled connection for $key after ${acquireTimeoutMs()}ms " +
            s"(maxTotal=${poolConfig.maxTotal})",
        )
      try {
        val remaining = deadlineNanos - java.lang.System.nanoTime()
        if (!permitsFor(key).tryAcquire(remaining, TimeUnit.NANOSECONDS))
          throw new TimeoutException(
            s"Timed out acquiring a pooled connection for $key after ${acquireTimeoutMs()}ms " +
              s"(maxPerHost=${poolConfig.maxPerHost})",
          )
      } catch {
        case timeout: TimeoutException =>
          globalPermits.release()
          throw timeout
      }
      checkedOut.incrementAndGet()
      val existing      = lock.synchronized {
        sweepIdleLocked()
        popLiveLocked(key)
      }
      existing match {
        case Some(conn) => new Checkout(key, conn, null)
        case None       =>
          if (isClosed) {
            checkedOut.decrementAndGet()
            permitsFor(key).release()
            globalPermits.release()
            throw new IllegalStateException("PooledLoomH2Client is closed")
          }
          try {
            new Checkout(key, openConnection(key), null)
          } catch {
            case failure: Throwable =>
              checkedOut.decrementAndGet()
              permitsFor(key).release()
              globalPermits.release()
              throw failure
          }
      }
    } finally {
      queued.decrementAndGet()
    }
  }

  /** Drops connections idle past `idleTimeout`; caller holds `lock`. */
  private def sweepIdleLocked(): Unit = {
    val idleNanos = poolConfig.idleTimeout.toNanos
    val now       = java.lang.System.nanoTime()
    idle.foreach { case (_, conns) =>
      var index = 0
      while (index < conns.size) {
        if (now - conns(index).lastUsedNanos > idleNanos) {
          conns(index).close()
          conns.remove(index)
        } else index += 1
      }
    }
  }

  /**
   * Pops a live idle connection for `key`; closes dead ones. Caller holds
   * `lock`.
   */
  private def popLiveLocked(key: PoolKey): Option[PooledH2Connection] = {
    idle.get(key) match {
      case None        => None
      case Some(conns) =>
        var result: Option[PooledH2Connection] = None
        while (result.isEmpty && conns.nonEmpty) {
          val candidate = conns.remove(conns.size - 1)
          if (!candidate.isDead && !candidate.isClosed) result = Some(candidate)
          else candidate.close()
        }
        if (conns.isEmpty) idle.remove(key)
        result
    }
  }

  private def returnIdle(checkout: Checkout): Unit = {
    checkout.conn.lastUsedNanos = java.lang.System.nanoTime()
    lock.synchronized {
      if (!closed && !checkout.conn.isDead && !checkout.conn.isClosed)
        idle.getOrElseUpdate(checkout.key, ListBuffer.empty) += checkout.conn
      else checkout.conn.close()
    }
    permitsFor(checkout.key).release()
    globalPermits.release()
    checkedOut.decrementAndGet()
  }

  /**
   * Opens (and handshakes) a fresh connection for `key`, honouring the ALPN
   * policy exactly like [[LoomH2ClientDriver]]. Returns `Left` with a one-shot
   * h1.1 fallback client when the peer negotiates away from H2 under
   * [[ClientAlpnPolicy.H2PreferredWithH11Fallback]]; the caller releases its
   * slot pair before delegating.
   */
  private def openConnection(key: PoolKey): PooledH2Connection =
    key.scheme match {
      case "http"  => openPlain(key.host, key.port)
      case "https" => openTls(key.host, key.port)
      case other   => throw new IllegalArgumentException(s"PooledLoomH2Client supports http/https only, got '$other'")
    }

  private def connectPlain(host: String, port: Int): Socket = {
    val socket = new Socket()
    try {
      socket.connect(new InetSocketAddress(host, port), clampMillis(config.effectiveConnectTimeout.toMillis))
    } catch {
      case timeout: java.net.SocketTimeoutException =>
        closeQuietly(socket)
        throw new TimeoutException(s"H2 connect to $host:$port timed out: ${timeout.getMessage}")
    }
    socket.setSoTimeout(clampMillis(config.effectiveRequestTimeout.toMillis))
    socket
  }

  private def openPlain(host: String, port: Int): PooledH2Connection = {
    val socket = connectPlain(host, port)
    try {
      PooledH2Connection.handshake(socket, socket.getInputStream, socket.getOutputStream)
    } catch {
      case NonFatal(failure) if config.alpn == ClientAlpnPolicy.H2PreferredWithH11Fallback =>
        closeQuietly(socket)
        throw H11FallbackSignal(host, port, failure)
      case NonFatal(failure)                                                               =>
        closeQuietly(socket)
        throw new IOException(s"H2C prior-knowledge exchange failed under ${config.alpn}: $failure")
    }
  }

  private def openTls(host: String, port: Int): PooledH2Connection = {
    val context = ClientTlsSupport.resolveContext(config.tls, sslContextOverride)
    val raw     = new Socket()
    try {
      raw.connect(new InetSocketAddress(host, port), clampMillis(config.effectiveConnectTimeout.toMillis))
    } catch {
      case timeout: java.net.SocketTimeoutException =>
        closeQuietly(raw)
        throw new TimeoutException(s"H2 connect to $host:$port timed out: ${timeout.getMessage}")
    }
    raw.setSoTimeout(clampMillis(config.effectiveRequestTimeout.toMillis))
    val socket  = context.getSocketFactory.createSocket(raw, host, port, true).asInstanceOf[SSLSocket]
    try {
      socket.setSSLParameters(ClientTlsSupport.alpnParameters(config.alpn.alpnProtocols, config.tls, context))
      socket.setUseClientMode(true)
      socket.startHandshake()
      socket.getApplicationProtocol match {
        case "h2"       =>
          PooledH2Connection.handshake(socket, socket.getInputStream, socket.getOutputStream)
        case "http/1.1" =>
          closeQuietly(socket)
          config.alpn match {
            case ClientAlpnPolicy.H2PreferredWithH11Fallback => throw H11FallbackSignal(host, port, null)
            case policy                                      =>
              throw new SSLHandshakeException(
                s"ClientAlpnPolicy $policy forbids the negotiated protocol 'http/1.1' (server $host:$port)",
              )
          }
        case other      =>
          closeQuietly(socket)
          val want =
            if (config.alpn == ClientAlpnPolicy.H2PreferredWithH11Fallback) "'h2' or 'http/1.1'" else "'h2'"
          throw new SSLHandshakeException(
            s"Unexpected ALPN protocol '$other' negotiated with $host:$port under ${config.alpn}; expected $want",
          )
      }
    } catch {
      case fallback: H11FallbackSignal => throw fallback
      case NonFatal(failure)           =>
        closeQuietly(socket)
        throw failure
    }
  }

  private def h11Fallback(): Client =
    sslContextOverride match {
      case Some(context) => JavaH2Client.h11(config, context)
      case None          => JavaH2Client.h11(config)
    }

  private def clampMillis(millis: Long): Int =
    math.min(math.max(millis, 1L), Int.MaxValue.toLong).toInt

  private def closeQuietly(socket: Socket): Unit =
    if (socket != null) {
      try socket.close()
      catch { case _: Throwable => () }
    }

  /**
   * A leased slot pair plus its connection. `finish()` is idempotent and the
   * single funnel for slot release: clean connections (pool open, conn alive,
   * body fully consumed or empty) return to idle, everything else is evicted.
   */
  private final class Checkout(val key: PoolKey, var conn: PooledH2Connection, var bodyInput: H2BodyInput) {
    private val done = new AtomicBoolean(false)

    def finish(): Unit =
      if (done.compareAndSet(false, true)) {
        val input = bodyInput
        if (!isClosed && !conn.isDead && !conn.isClosed && (input == null || input.completedCleanly))
          returnIdle(this)
        else {
          conn.close()
          permitsFor(key).release()
          globalPermits.release()
          checkedOut.decrementAndGet()
        }
      }

    /** Evicts the connection but keeps the slot pair for an immediate retry. */
    def evictConnOnly(): Unit = {
      conn.close()
      conn.markDead()
    }
  }

  /**
   * An in-flight request with an explicit cancel handle. Threading: `await`
   * runs the HEADERS phase on the calling (virtual) thread; `cancel` may run
   * anywhere and wins races via CAS - late cancels still release the slot.
   */
  final class CancellableExchange private[PooledLoomH2Client] (
    key: PoolKey,
    request: Request,
  ) {
    private val checkout                   = PooledLoomH2Client.this.checkout(key)
    // Shared with the connection exchange (when created): the reader maps a
    // dead socket to CancellationException through this flag even when cancel
    // lands before the exchange's RST hook exists.
    private val cancelled                  = new AtomicBoolean(false)
    @volatile private var hook: () => Unit = () => ()

    /**
     * Sends RST_STREAM when the exchange exists yet, then closes the socket
     * (unblocking an in-flight read, which maps to
     * [[java.util.concurrent.CancellationException]] via `cancelled`), and
     * releases the pool slot. Idempotent.
     */
    def cancel(): Unit =
      if (cancelled.compareAndSet(false, true)) {
        try hook()
        finally {
          checkout.conn.closeSocket()
          checkout.finish()
        }
      }

    /**
     * Blocks for the response HEADERS and returns the response. The body -
     * unless empty - streams lazily and holds the pool slot until fully
     * consumed (or cancelled / failed). Throws [[StaleConnectionException]]-
     * free errors: pre-flush transport failures retry once on a fresh
     * connection; [[TimeoutException]] on deadline expiry;
     * [[java.util.concurrent.CancellationException]] when cancelled.
     */
    def await(): Response = {
      if (cancelled.get()) {
        checkout.finish()
        throw new java.util.concurrent.CancellationException("H2 request was cancelled before sending")
      }
      try {
        awaitOnce()
      } catch {
        case _: StaleConnectionException =>
          // The server never saw the request: replace the evicted connection
          // and try exactly once more on the same slot pair.
          checkout.evictConnOnly()
          try {
            checkout.conn = openConnection(key)
          } catch {
            case fallback: H11FallbackSignal =>
              checkout.finish()
              return h11Fallback().send(request)
            case failure: Throwable          =>
              checkout.finish()
              throw failure
          }
          try {
            awaitOnce()
          } catch {
            case fallback: H11FallbackSignal =>
              checkout.finish()
              h11Fallback().send(request)
            case failure: Throwable          =>
              checkout.finish()
              throw failure
          }
        case fallback: H11FallbackSignal =>
          checkout.finish()
          h11Fallback().send(request)
        case failure: Throwable          =>
          checkout.finish()
          throw failure
      }
    }

    private def awaitOnce(): Response = {
      val defaultPort = if (key.scheme == "http") 80 else 443
      val url         = request.url
      val schemeText  = url.scheme
        .map(_.text)
        .getOrElse(
          throw new IllegalArgumentException("URL carries no scheme"),
        )
      val hostText    = url.host.getOrElse(throw new IllegalArgumentException("URL carries no host"))
      val portPart    = url.port.filter(_ != defaultPort).map(":" + _).getOrElse("")
      val pathPart    = {
        val encoded = url.path.encode
        if (encoded.isEmpty) "/" else encoded
      }
      val query       = url.queryParams.encode
      val target      = if (query.nonEmpty) pathPart + "?" + query else pathPart
      val authority   = hostText + portPart

      val exchange = checkout.conn.exchange(
        request,
        schemeText,
        authority,
        target,
        clampMillis(config.effectiveRequestTimeout.toMillis),
        config.effectiveStreamTimeout.map(_.toMillis),
        cancelled,
      )
      hook = exchange.cancel
      if (cancelled.get()) {
        exchange.cancel()
        throw new java.util.concurrent.CancellationException("H2 request was cancelled")
      }
      if (exchange.endStream) {
        checkout.bodyInput = null
        val response = Response(
          status = exchange.status,
          headers = exchange.headers,
          body = Body.fromArray(Array.emptyByteArray, exchange.contentType),
          version = Version.`HTTP/2.0`,
        )
        checkout.finish()
        response
      } else {
        checkout.bodyInput = exchange.bodyInput.orNull
        val released                                         = new AtomicBoolean(false)
        // fromReader keeps the error channel at Nothing: transport failures
        // surface as thrown defects (uniform with the blocking Client style),
        // and pulls stay chunk-granular (heap-bounded, no per-byte boxing).
        val stream: zio.blocks.streams.Stream[Nothing, Byte] =
          zio.blocks.streams.Stream.fromReader(
            zio.blocks.streams.io.Reader.fromInputStream(exchange.bodyInput.orNull),
          )
        val lazyBody = stream.ensuring(if (released.compareAndSet(false, true)) checkout.finish())
        Response(
          status = exchange.status,
          headers = exchange.headers,
          body = Body.fromStream(lazyBody, exchange.contentType),
          version = Version.`HTTP/2.0`,
        )
      }
    }
  }

  /**
   * H2C/TLS-h2 both failed under the fallback policy: the slot pair is already
   * released; delegate one-shot to the JDK h1.1 leg. An internal control
   * signal, never surfaced (callers translate it before it escapes).
   */
  private case class H11FallbackSignal(host: String, port: Int, cause: Throwable)
      extends IOException(
        s"H2 unavailable for $host:$port under ${config.alpn}; delegating to the JDK h1.1 leg",
        cause,
      )
}

@experimental
object PooledLoomH2Client {

  final case class Stats(checkedOut: Int, idle: Int, queued: Int)

  def apply(config: ClientConfig): PooledLoomH2Client =
    new PooledLoomH2Client(config, None)

  /**
   * Test/ops seam mirroring [[LoomH2ClientDriver.apply]]: a caller-provided
   * [[SSLContext]] is wired programmatically at construction time, outside
   * structured config.
   */
  def apply(config: ClientConfig, sslContext: SSLContext): PooledLoomH2Client =
    new PooledLoomH2Client(config, Some(sslContext))
}

/**
 * Pool authority key (scheme/host/port). Top-level so the pool class can use it
 * unqualified.
 */
private[http] final case class PoolKey(scheme: String, host: String, port: Int) {
  override def toString: String = s"$scheme://$host:$port"
}
