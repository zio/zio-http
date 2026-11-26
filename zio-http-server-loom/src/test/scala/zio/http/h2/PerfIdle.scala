package zio.http.h2

import java.io.{InputStream, OutputStream}
import java.lang.management.ManagementFactory
import java.util.concurrent.locks.LockSupport

import scala.jdk.CollectionConverters._

import zio.blocks.mux.Mux

/**
 * In-process perf harness for the Loom H2 connection.
 *
 * Isolates per-connection costs without sockets/network:
 *   - `idle N secs` : N connections parked in read; measures process CPU
 *     (writer poll).
 *   - `feed N frames`: N connections fed PING frames; measures GC count/time
 *     (read-buffer alloc).
 *
 * Run via: java -cp <serverLoom.test classpath> zio.http.h2.PerfIdle <mode> <n>
 * <x>
 */
object PerfIdle {
  private val Preface  = "PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n".getBytes("US-ASCII")
  private val Settings = FrameCodec.encode(H2Frame.Settings(ack = false, Nil)).toArray
  private val Initial  = Preface ++ Settings

  /**
   * Returns the client preface + SETTINGS on first reads, then blocks forever
   * (idle).
   */
  final class FakeIdleIn extends InputStream {
    private var i                                              = 0
    override def read(): Int                                   =
      if (i < Initial.length) { val b = Initial(i) & 0xff; i += 1; b }
      else { LockSupport.park(); -1 }
    override def read(b: Array[Byte], off: Int, len: Int): Int =
      if (i < Initial.length) {
        val n = math.min(len, Initial.length - i)
        System.arraycopy(Initial, i, b, off, n)
        i += n
        n
      } else { LockSupport.park(); -1 }
  }

  /**
   * Returns preface + SETTINGS first, then one PING frame per read (bounded by
   * totalFrames).
   */
  final class FakeFeedIn(totalFrames: Long, throttleNanos: Long) extends InputStream {
    private val ping         = Array[Byte](0, 0, 8, 6, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)
    private var i            = 0
    private var sent         = 0L
    private var nextWake     = 0L
    override def read(): Int = read(new Array[Byte](1), 0, 1)
    override def read(b: Array[Byte], off: Int, len: Int): Int =
      if (i < Initial.length) {
        val n = math.min(len, Initial.length - i)
        System.arraycopy(Initial, i, b, off, n)
        i += n
        n
      } else if (sent >= totalFrames) {
        LockSupport.park(); -1
      } else {
        if (throttleNanos > 0) {
          val now = System.nanoTime()
          if (nextWake == 0L) nextWake = now + throttleNanos
          else if (now < nextWake) { LockSupport.parkNanos(nextWake - now); nextWake += throttleNanos }
          else nextWake = now + throttleNanos
        }
        sent += 1
        System.arraycopy(ping, 0, b, off, ping.length)
        ping.length
      }
  }

  final class NullOut extends OutputStream {
    override def write(b: Int): Unit                             = ()
    override def write(b: Array[Byte], off: Int, len: Int): Unit = ()
  }

  private def os = ManagementFactory.getOperatingSystemMXBean.asInstanceOf[com.sun.management.OperatingSystemMXBean]

  def main(args: Array[String]): Unit = {
    val mode = if (args.nonEmpty) args(0) else "idle"
    val n    = if (args.length > 1) args(1).toInt else 1000
    val x    = if (args.length > 2) args(2).toLong else 10L

    mode match {
      case "idle"   => idle(n, x.toInt)
      case "feed"   => feed(n, x)
      case "timers" => timers(n, x.toInt)
      case other    => println(s"unknown mode: $other (use idle|feed|timers)"); sys.exit(2)
    }
    sys.exit(0)
  }

  private def timers(n: Int, secs: Int): Unit = {
    // One control, N request deadlines started and never cancelled: with the old
    // implementation this parks N virtual threads; with the shared scheduler it
    // creates zero VTs until a deadline actually fires.
    val control = new H2ConnectionControl(
      new NullOut,
      Mux[Int, H2Frame, H2Frame](n * 2 + 16),
      requestTimeoutMs = 600000L,
    )
    val keep    = new java.util.ArrayList[Any]()
    var i       = 0
    while (i < n) {
      keep.add(control.startRequestTimer(i * 2 + 1))
      i += 1
    }
    println(s"started $n request timers; sleeping $secs s (VT dump sampled externally)")
    Thread.sleep(secs * 1000L)
    println(s"timers done (kept ${keep.size()} futures)")
  }

  private def idle(n: Int, secs: Int): Unit = {
    var i   = 0
    while (i < n) {
      val conn = new H2Connection(new FakeIdleIn, new NullOut)
      Thread.ofVirtual().name("perf-conn-" + i).start(() => conn.run(_ => ()))
      i += 1
    }
    Thread.sleep(2500) // warm up VTs + JIT
    val c0  = os.getProcessCpuTime
    Thread.sleep(secs * 1000L)
    val c1  = os.getProcessCpuTime
    val cpu = (c1 - c0) / 1e9
    println(
      f"mode=idle conns=$n secs=$secs cpu_seconds=$cpu%.3f cpu_per_conn_millicores=${cpu * 1000.0 / secs / n}%.4f wakeups_per_conn_per_sec~=${cpu * 1000.0 / secs / n}%.1f",
    )
  }

  private def feed(n: Int, totalFramesPerConn: Long): Unit = {
    val totalFrames = totalFramesPerConn
    val conns       = new Array[H2Connection](n)
    var i           = 0
    while (i < n) {
      val conn = new H2Connection(new FakeFeedIn(totalFrames, throttleNanos = 200000L), new NullOut)
      conns(i) = conn
      Thread.ofVirtual().name("perf-feed-" + i).start(() => conn.run(_ => ()))
      i += 1
    }
    Thread.sleep(1500)
    val gc          = ManagementFactory.getGarbageCollectorMXBeans.asScala
    def gcCount     = gc.map(_.getCollectionCount).sum
    def gcTime      = gc.map(_.getCollectionTime).sum
    val c0          = os.getProcessCpuTime; val g0 = gcCount; val t0 = gcTime; val n0 = System.nanoTime
    Thread.sleep((totalFramesPerConn * 200000L / 1000000000L + 3L) * 1000L) // ~feed duration + margin
    val c1   = os.getProcessCpuTime; val g1 = gcCount; val t1 = gcTime; val n1 = System.nanoTime
    val wall = (n1 - n0) / 1e9
    println(
      f"mode=feed conns=$n frames_per_conn=$totalFrames wall=${wall}%.2f cpu_seconds=${(c1 - c0) / 1e9}%.3f " +
        f"gc_count=${g1 - g0} gc_time_ms=${t1 - t0}",
    )
  }
}
