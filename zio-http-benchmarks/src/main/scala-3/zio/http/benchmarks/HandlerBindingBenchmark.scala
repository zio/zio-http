/*
 * Copyright 2026 the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package zio.http.benchmarks

import java.util.concurrent.TimeUnit

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole

import zio.blocks.context.Context
import zio.blocks.endpoint.PathCodec._
import zio.blocks.endpoint.RoutePattern.{MethodSyntax, RoutePatternOps}
import zio.blocks.scope.{Scope => BlocksScope}

// NOTE (Todo 7 finding, reproduced here): Scala 3's `RouteBinding.handler` shares its simple name
// with the pre-existing `zio.http.handler` (the old `ToHandler`-based package function) - combining
// a wildcard `import zio.http._` with `import zio.http.RouteBinding._` makes `handler` genuinely
// ambiguous. Fix: import only the specific `zio.http` names actually needed here (no wildcard).
import zio.http.{Handler, Method, Request, Response, Route, URL}
import zio.http.RouteBinding._

/**
 * Todo 8 (route-pattern-typed-vars, D8) - Scala 3 JMH allocation-neutrality
 * verification.
 *
 * Isolates the macro-generated `Extracted`/handler-invocation call site (the
 * ONLY thing this benchmark measures - `pattern.decode` URL-parsing overhead is
 * deliberately excluded from every benchmark method by pre-building the
 * already-decoded `vars` value, matching how zio-http's real request-dispatch
 * path calls `handler.handle` with an already-decoded value) for Worked Example
 * 2 (2-var full-use handler, `GET / int("userId") / string("postId") ->
 * handler((userId: Int, postId: String) => ...)`), comparing THREE
 * handler-invocation shapes over the IDENTICAL `(Int, String)` input:
 *   - `macroGenerated`: the real `pattern -> handler(fn)` macro output (Todo
 *     4's `RouteBinding`).
 *   - `handWritten`: a hand-written `Handler.extracted[Ctx, (Int, String)]`
 *     baseline that accesses the real value tuple directly
 *     (`vars._1`/`vars._2`), with NO reshaping - the allocation floor any
 *     macro-generated call site should match exactly (D8: zero delta, not
 *     "close enough").
 *   - `naiveMapAllocating`: a DELIBERATELY non-optimized variant that boxes the
 *     extracted vars into a `scala.collection.immutable.Map[String, Any]`
 *     before use - the harness self-test proving a real regression (a genuine
 *     `Map` allocation) is NOT masked/averaged-away by JMH noise.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
class HandlerBindingBenchmark {

  private val macroRoute: Route[Any] =
    Method.GET / int("userId") / string("postId") ->
      handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))

  private val handWrittenRoute: Route[Any] = Route(
    Method.GET / int("userId") / string("postId"),
    Handler.extracted[Any, (Int, String)] { (_, _, vars, _) =>
      Response.text(s"user=${vars._1} post=${vars._2}")
    },
  )

  private val request: Request      = Request.get(URL.root)
  private val context: Context[Any] = Context.empty
  private val scope: BlocksScope    = BlocksScope.global
  private val vars: (Int, String)   = (42, "abc")

  @Benchmark
  def macroGenerated(bh: Blackhole): Unit =
    bh.consume(macroRoute.handler.handle(request, context, vars, scope))

  @Benchmark
  def handWritten(bh: Blackhole): Unit =
    bh.consume(handWrittenRoute.handler.handle(request, context, vars, scope))

  /**
   * Harness self-test (D8's required "confirm the harness has teeth" check):
   * deliberately allocates a `Map` per invocation, so `-prof gc` MUST show a
   * nonzero B/op delta vs `handWritten` - if it did not, the harness itself
   * would be broken.
   */
  @Benchmark
  def naiveMapAllocating(bh: Blackhole): Unit = {
    val (userId, postId) = vars
    val paramsMap        = scala.collection.immutable.Map("userId" -> userId, "postId" -> postId)
    bh.consume(Response.text(s"user=${paramsMap("userId")} post=${paramsMap("postId")}"))
  }
}
