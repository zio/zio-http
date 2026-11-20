package example

import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

import zio._

import zio.stream.ZStream

import zio.http._
import zio.http.netty.NettyBody

import io.netty.buffer.{ByteBuf, PooledByteBufAllocator}

/**
 * Streams a response from off-heap (direct) memory to the socket with no JVM
 * heap copy, using `NettyBody.fromByteBufStream`.
 *
 * Flow of one request:
 *
 *   1. A producer fills direct `ByteBuf`s (off-heap). Here it is a loop that
 *      writes text; in a real application it is a native library (a database,
 *      an Arrow exporter, a codec) handing you memory it allocated.
 *   2. Each buffer is put into a per-request [[OffHeapStreaming.Registry]] and
 *      only its ticket (a `Long`) travels down the `ZStream`. A ticket is a
 *      plain value, so a ticket dropped by an interrupt or a failure leaks
 *      nothing.
 *   3. The writer calls `claim(ticket)` and hands the buffer to Netty in one
 *      synchronous step. Netty writes it as one HTTP chunk (gathering write,
 *      the kernel does the only copy) and releases it afterwards.
 *   4. When the stream ends, for any reason, the registry releases every buffer
 *      that was never claimed.
 *
 * Try it:
 * {{{
 *   sbt "zioHttpExample/runMain example.OffHeapStreaming"
 *   curl -sN 'http://127.0.0.1:8080/offheap?chunks=5' | head
 *   curl -s  'http://127.0.0.1:8080/offheap?chunks=2000' | wc -c   # ~128 MiB, chunked
 * }}}
 * Stop curl half-way (Ctrl-C): the stream is interrupted and the registry
 * releases any buffer that was produced but not yet claimed.
 */
object OffHeapStreaming extends ZIOAppDefault {

  /** Bytes per chunk. Each chunk is one direct buffer and one HTTP chunk. */
  val ChunkSize: Int = 64 * 1024

  /**
   * The producer-owned home of the buffers of ONE request: buffers are
   * registered here and only their tickets are streamed.
   *
   * Ownership moves exactly once: `take` (an atomic `remove`) gives the buffer
   * to the writer, which hands it to Netty. `releaseAll` releases whatever was
   * never taken. Both go through `remove`, so no buffer is released twice.
   */
  final case class Registry(live: ConcurrentHashMap[java.lang.Long, ByteBuf], next: AtomicLong) {

    def register(buf: ByteBuf): Long = {
      val ticket = next.getAndIncrement()
      live.put(ticket, buf)
      ticket
    }

    /** The `claim` function of `fromByteBufStream`: at most once per ticket. */
    def take(ticket: Long): Option[ByteBuf] = Option(live.remove(ticket))

    def releaseAll(): Unit =
      live.keySet().forEach(id => Option(live.remove(id)).foreach(_.release()))

    def unclaimed: Int = live.size()
  }

  object Registry {
    val make: ZIO[Any, Nothing, Registry] =
      ZIO.succeed(Registry(new ConcurrentHashMap[java.lang.Long, ByteBuf](), new AtomicLong(0L)))
  }

  /**
   * The stand-in for native code: allocates a DIRECT buffer (off-heap, no
   * backing array) and fills it. The bytes are written straight into off-heap
   * memory; they never exist as a `Chunk[Byte]` or `Array[Byte]`.
   */
  def produce(index: Int): ZIO[Any, Throwable, ByteBuf] =
    ZIO.attempt {
      val buf  = PooledByteBufAllocator.DEFAULT.directBuffer(ChunkSize, ChunkSize)
      val line = f"chunk $index%06d: off-heap bytes written to the socket without a JVM copy%n"
      val text = buf.writeCharSequence(line, StandardCharsets.US_ASCII)
      buf.writeZero(ChunkSize - text - 1).writeByte('\n')
    }

  /**
   * The response body: a stream of tickets; each buffer is produced on demand
   * (backpressure: the next one is produced only after the previous chunk was
   * flushed), registered, and claimed by the writer.
   */
  def offHeapBody(chunks: Int): ZIO[Any, Nothing, Body] =
    Registry.make.map { registry =>
      val tickets: ZStream[Any, Throwable, Long] =
        ZStream
          .range(0, chunks)
          .mapZIO(i => produce(i).map(registry.register))
          .ensuring(
            ZIO.succeed(registry.unclaimed).flatMap { left =>
              ZIO.succeed(registry.releaseAll()) *>
                ZIO.logInfo(s"offheap stream of $chunks chunks ended; released $left unclaimed buffer(s)")
            },
          )

      NettyBody.fromByteBufStream(tickets)(registry.take)
    }

  val routes: Routes[Any, Response] = Routes(
    Method.GET / "offheap" -> handler { (req: Request) =>
      val chunks = req.queryOrElse[Int]("chunks", 16)
      if (chunks < 0) ZIO.succeed(Response.badRequest("chunks must be >= 0"))
      else
        offHeapBody(chunks).map { body =>
          // The response headers are not derived from the body: set Content-Type on the Response.
          Response(status = Status.Ok, body = body).contentType(MediaType.application.`octet-stream`)
        }
    },
  )

  override val run: ZIO[Any, Throwable, Nothing] =
    Server.serve(routes).provide(Server.defaultWith(_.binding("127.0.0.1", 8080)))
}
