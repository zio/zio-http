package zio.http.h2

import scala.annotation.experimental

import zio.blocks.telemetry.{AttributeValue, ConsoleLogRecordProcessor, LoggerProvider, SpanKind, metric, trace}

/** Microbenchmark for the per-request telemetry that `H2Transport.instrumentRequest`
  * used to run unconditionally: an always-on span + 6 attributes, a 4-label
  * counter increment, and a stdout log line.
  *
  *   java -cp <serverLoom.test cp> zio.http.h2.PerfTelemetry <span|metric|log|all> <iters>
  *
  * Redirect stdout to /dev/null for the log/all modes (each iteration writes a line).
  */
@experimental
object PerfTelemetry {
  private val logger  = LoggerProvider.builder.addLogRecordProcessor(new ConsoleLogRecordProcessor).build().get("perf")
  private val counter = metric.counter("perf.requests")

  def main(args: Array[String]): Unit = {
    val mode  = if (args.nonEmpty) args(0) else "all"
    val iters = if (args.length > 1) args(1).toInt else 20000
    val op: () => Unit = mode match {
      case "span"   => spanOp
      case "metric" => metricOp
      case "log"    => logOp
      case _        => allOp
    }
    var i = 0
    while (i < 5000) { op(); i += 1 }
    val t0 = System.nanoTime
    i = 0
    while (i < iters) { op(); i += 1 }
    val dt = (System.nanoTime - t0) / 1e9
    println(f"mode=$mode iters=$iters secs=$dt ops_per_sec=${iters / dt}%.0f ns_per_op=${dt * 1e9 / iters}%.1f")
  }

  private def spanOp: () => Unit = () =>
    trace.span("http.request", SpanKind.Server) { span =>
      span.setAttribute("http.request.method", "GET")
      span.setAttribute("url.path", "/feed")
      span.setAttribute("network.protocol.name", "h2")
      span.setAttribute("http.response.status_code", 200L)
      span.setAttribute("http.server.active_connections", 1L)
      span.setAttribute("http.server.duration_ms", 1L)
    }

  private def metricOp: () => Unit = () =>
    counter.add(1L, "method" -> "GET", "path" -> "/feed", "status" -> 200, "protocol" -> "h2")

  private def logOp: () => Unit = () =>
    logger.info(
      "HTTP request",
      "method"      -> AttributeValue.StringValue("GET"),
      "path"        -> AttributeValue.StringValue("/feed"),
      "status"      -> AttributeValue.LongValue(200L),
      "duration_ms" -> AttributeValue.LongValue(1L),
    )

  private def allOp: () => Unit = () => { spanOp(); metricOp(); logOp() }
}
