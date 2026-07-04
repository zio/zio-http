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
import zio.blocks.endpoint.RoutePattern.MethodSyntax
import zio.blocks.scope.{Scope => BlocksScope}

import zio.http.Method.GET
import zio.http.PathVarHandler.handler
import zio.http.ResultType._
import zio.http.RouteBinding._
import zio.http._

/**
 * Todo 8 (route-pattern-typed-vars, D8) - Scala 2.13 parity JMH allocation-neutrality
 * verification. Mirrors the Scala 3 benchmark in `scala-3/.../HandlerBindingBenchmark.scala`
 * exactly (same three shapes, same input, same rationale) - see that file's doc comment for the
 * full explanation.
 */
@BenchmarkMode(Array(Mode.AverageTime))
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Measurement(iterations = 5, time = 300, timeUnit = TimeUnit.MILLISECONDS)
@Fork(value = 2)
class HandlerBindingBenchmark {

  private val macroRoute: Route[Any] =
    GET / int("userId") / string("postId") ->
      handler((userId: Int, postId: String) => Response.text(s"user=$userId post=$postId"))

  private val handWrittenRoute: Route[Any] = Route(
    GET / int("userId") / string("postId"),
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

  /** Harness self-test (D8's required "confirm the harness has teeth" check): deliberately
    * allocates a `Map` per invocation, so `-prof gc` MUST show a nonzero B/op delta vs
    * `handWritten` - if it did not, the harness itself would be broken.
    */
  @Benchmark
  def naiveMapAllocating(bh: Blackhole): Unit = {
    val (userId, postId) = vars
    val paramsMap         = scala.collection.immutable.Map("userId" -> userId, "postId" -> postId)
    bh.consume(Response.text(s"user=${paramsMap("userId")} post=${paramsMap("postId")}"))
  }
}
