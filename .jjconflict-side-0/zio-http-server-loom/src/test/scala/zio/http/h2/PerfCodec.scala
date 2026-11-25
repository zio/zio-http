package zio.http.h2

import scala.annotation.experimental

import zio.blocks.chunk.Chunk
import zio.http.h2.hpack.{HeaderField, HpackCodec}

/** Microbenchmark for the per-request wire codec (HPACK + frame encode).
  *   java -cp <serverLoom.test cp> zio.http.h2.PerfCodec hpack 500000
  *   java -cp <serverLoom.test cp> zio.http.h2.PerfCodec frame 500000
  *
  * Header values vary per iteration so HPACK cannot just hit the dynamic table:
  * this mirrors a real response (changing content-length/date) and exercises
  * name/exact index lookups on every encode.
  */
@experimental
object PerfCodec {
  def main(args: Array[String]): Unit = {
    val mode  = if (args.nonEmpty) args(0) else "hpack"
    val iters = if (args.length > 1) args(1).toInt else 200000
    mode match {
      case "hpack" => hpack(iters)
      case "frame" => frame(iters)
      case other   => println(s"unknown mode: $other"); sys.exit(2)
    }
  }

  private def headersFor(i: Int): List[HeaderField] =
    List(
      HeaderField(":status", "200"),
      HeaderField("content-type", "application/json"),
      HeaderField("content-length", (1000 + (i & 4095)).toString),
      HeaderField("date", "Mon, 24 Nov 2026 00:00:" + (i & 59).toString),
      HeaderField("server", "zio-http"),
    )

  private def hpack(n: Int): Unit = {
    val variants           = Array.tabulate(1024)(headersFor)
    val enc                = new HpackCodec()
    val dec                = new HpackCodec()
    var block: Chunk[Byte] = Chunk.empty
    var i                  = 0
    while (i < 4096) { block = enc.encode(variants(i & 1023)); dec.decode(block); i += 1 }
    val t0 = System.nanoTime
    i = 0
    while (i < n) { block = enc.encode(variants(i & 1023)); dec.decode(block); i += 1 }
    val dt = (System.nanoTime - t0) / 1e9
    println(f"mode=hpack iters=$n secs=$dt ops_per_sec=${n / dt}%.0f ns_per_op=${dt * 1e9 / n}%.1f")
  }

  private def frame(n: Int): Unit = {
    val body         = Chunk.fromArray(Array.fill[Byte](128)(0x41.toByte))
    val data         = H2Frame.Data(1, body, endStream = true)
    val headerBlock  = new HpackCodec().encode(List(HeaderField(":status", "200"), HeaderField("content-type", "text/plain")))
    val headers      = H2Frame.Headers(1, headerBlock, endStream = true, endHeaders = true)
    var acc          = 0L
    var i            = 0
    while (i < 20000) { acc += FrameCodec.encode(data).length; acc += FrameCodec.encode(headers).length; i += 1 }
    val t0 = System.nanoTime
    i = 0
    while (i < n) { acc += FrameCodec.encode(data).length; acc += FrameCodec.encode(headers).length; i += 1 }
    val dt = (System.nanoTime - t0) / 1e9
    println(f"mode=frame iters=$n secs=$dt ops_per_sec=${n / dt}%.0f ns_per_op=${dt * 1e9 / n}%.1f acc=$acc")
  }
}
