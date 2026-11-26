package zio.http.h2

import java.io.{InputStream, OutputStream}
import java.util.concurrent.{ConcurrentLinkedQueue, CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}
import java.util.concurrent.locks.LockSupport

import zio.blocks.chunk.Chunk
import zio.blocks.mux.MuxStream
import zio.http.h2.hpack.{HeaderField, Hpack}

/**
 * SSE fan-out benchmark: N long-lived streams, one producer broadcasting events
 * (the Datastar push shape). Each connection holds a parked reader VT and a
 * parked stream VT (the handler waits until the benchmark ends), matching a
 * real `text/event-stream` lifecycle. Measures broadcast events/s and CPU.
 *
 * java -cp <serverLoom.test cp> zio.http.h2.PerfSse <conns> <seconds> <tickMs>
 */
object PerfSse {
  private val Preface = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes("US-ASCII")

  private def requestBytes(): Array[Byte] = {
    val settings = FrameCodec.encode(H2Frame.Settings(ack = false, Nil)).toArray
    val block    = Hpack
      .encode(
        List(
          HeaderField(":method", "GET"),
          HeaderField(":path", "/events"),
          HeaderField(":scheme", "http"),
          HeaderField(":authority", "bench"),
        ),
      )
      .toArray
    val headers  =
      FrameCodec.encode(H2Frame.Headers(1, Chunk.fromArray(block), endStream = true, endHeaders = true)).toArray
    Preface ++ settings ++ headers
  }

  final class OnceIn(initial: Array[Byte]) extends InputStream {
    private var i                                              = 0
    override def read(): Int                                   = read(new Array[Byte](1), 0, 1)
    override def read(b: Array[Byte], off: Int, len: Int): Int =
      if (i < initial.length) {
        val n = math.min(len, initial.length - i)
        System.arraycopy(initial, i, b, off, n)
        i += n
        n
      } else { LockSupport.park(); -1 }
  }

  final class NullOut extends OutputStream {
    override def write(b: Int): Unit                             = ()
    override def write(b: Array[Byte], off: Int, len: Int): Unit = ()
  }

  private def os = java.lang.management.ManagementFactory.getOperatingSystemMXBean
    .asInstanceOf[com.sun.management.OperatingSystemMXBean]

  def main(args: Array[String]): Unit = {
    val n       = if (args.nonEmpty) args(0).toInt else 1000
    val seconds = if (args.length > 1) args(1).toInt else 8
    val tickMs  = if (args.length > 2) args(2).toLong else 100L

    val ready   = new CountDownLatch(n)
    val done    = new CountDownLatch(1)
    val streams = new ConcurrentLinkedQueue[(H2Connection, MuxStream[Int, H2Frame, H2Frame])]()

    var i = 0
    while (i < n) {
      val conn = new H2Connection(new OnceIn(requestBytes()), new NullOut)
      Thread
        .ofVirtual()
        .name("perf-sse-conn-" + i)
        .start(() =>
          conn.run { stream =>
            streams.add((conn, stream))
            ready.countDown()
            try done.await()
            catch { case _: InterruptedException => () }
          },
        )
      i += 1
    }
    ready.await(30, TimeUnit.SECONDS)
    println(s"opened ${streams.size()}/$n streams")

    val payload  = Chunk.fromArray("data: event\n\n".getBytes("US-ASCII"))
    val count    = new AtomicLong(0)
    val stop     = new AtomicBoolean(false)
    val producer = Thread
      .ofPlatform()
      .name("perf-sse-producer")
      .start(() => {
        while (!stop.get()) {
          val it = streams.iterator()
          while (it.hasNext) {
            val (c, s) = it.next()
            try { c.writeDataFrame(s, H2Frame.Data(s.id, payload, endStream = false)); count.incrementAndGet() }
            catch { case _: Throwable => () }
          }
          try Thread.sleep(tickMs)
          catch { case _: InterruptedException => () }
        }
      })

    Thread.sleep(2000) // warm up
    val c0 = os.getProcessCpuTime
    val n0 = count.get
    Thread.sleep(seconds * 1000L)
    val c1 = os.getProcessCpuTime
    val n1 = count.get
    stop.set(true); producer.join(2000); done.countDown()

    val events = n1 - n0
    val cpu    = (c1 - c0) / 1e9
    println(
      f"mode=sse conns=$n events=$events events_per_sec=${events / seconds.toDouble}%.0f " +
        f"cpu_seconds=$cpu%.3f events_per_cpu_sec=${events / math.max(cpu, 1e-9)}%.0f",
    )
    sys.exit(0)
  }
}
