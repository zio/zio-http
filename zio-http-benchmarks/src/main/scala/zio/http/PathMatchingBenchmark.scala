/*
 * Copyright 2023 the ZIO HTTP contributors.
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
package zio.http

import org.openjdk.jmh.annotations._
import org.openjdk.jmh.infra.Blackhole
import java.util.concurrent.TimeUnit
import zio.Chunk

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
class PathMatchingBenchmark {

  // Test data setup
  var patterns: Seq[(RoutePattern[_], Int)] = _
  var stateMachine: StateMachineRouter.StateMachine[Int] = _
  var testPaths: Array[Path] = _

  @Setup
  def setup(): Unit = {
    // Create test patterns
    patterns = Seq(
      (Method.GET / "users", 1),
      (Method.GET / "users" / int("userId"), 2),
      (Method.GET / "users" / int("userId") / "posts", 3),
      (Method.GET / "users" / int("userId") / "posts" / string("postId"), 4),
      (Method.GET / "api" / "v1" / "products", 5),
      (Method.GET / "api" / "v1" / "products" / string("productId"), 6),
      (Method.GET / "api" / "v2" / "categories", 7),
      (Method.GET / "api" / "v2" / "categories" / string("categoryId") / "products", 8)
    )

    // Build state machine implementation
    stateMachine = StateMachineRouter.fromPatterns(patterns)

    // Create test paths
    testPaths = Array(
      Path("/users"),
      Path("/users/123"),
      Path("/users/123/posts"),
      Path("/users/123/posts/abc"),
      Path("/api/v1/products"),
      Path("/api/v1/products/xyz"),
      Path("/api/v2/categories"),
      Path("/api/v2/categories/electronics/products"),
      // Add some non-matching paths
      Path("/nonexistent"),
      Path("/users/abc"), // Invalid userId
      Path("/api/v3/unknown")
    )
  }

  @Benchmark
  def currentImplementation(bh: Blackhole): Unit = {
    testPaths.foreach { path =>
      bh.consume(patterns.find { case (pattern, _) => 
        pattern.pathCodec.segments == path.segments
      })
    }
  }

  @Benchmark
  def stateMachineImplementation(bh: Blackhole): Unit = {
    testPaths.foreach { path =>
      bh.consume(stateMachine.matchPath(path))
    }
  }

  @Benchmark
  def currentImplementationSinglePath(bh: Blackhole): Unit = {
    bh.consume(patterns.find { case (pattern, _) => 
      pattern.pathCodec.segments == Path("/users/123/posts/abc").segments
    })
  }

  @Benchmark
  def stateMachineImplementationSinglePath(bh: Blackhole): Unit = {
    bh.consume(stateMachine.matchPath(Path("/users/123/posts/abc")))
  }

  @Benchmark
  def currentImplementationNonMatchingPath(bh: Blackhole): Unit = {
    bh.consume(patterns.find { case (pattern, _) => 
      pattern.pathCodec.segments == Path("/nonexistent/path").segments
    })
  }

  @Benchmark
  def stateMachineImplementationNonMatchingPath(bh: Blackhole): Unit = {
    bh.consume(stateMachine.matchPath(Path("/nonexistent/path")))
  }

  @Benchmark
  def currentImplementationHighConcurrency(bh: Blackhole): Unit = {
    // Simulate high concurrency with multiple paths
    for (_ <- 1 to 1000) {
      testPaths.foreach { path =>
        bh.consume(patterns.find { case (pattern, _) => 
          pattern.pathCodec.segments == path.segments
        })
      }
    }
  }

  @Benchmark
  def stateMachineImplementationHighConcurrency(bh: Blackhole): Unit = {
    // Simulate high concurrency with multiple paths
    for (_ <- 1 to 1000) {
      testPaths.foreach { path =>
        bh.consume(stateMachine.matchPath(path))
      }
    }
  }
} 