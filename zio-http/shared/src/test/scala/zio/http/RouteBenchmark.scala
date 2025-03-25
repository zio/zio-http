package zio.http

import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit

@State(Scope.Thread)
@BenchmarkMode(Array(Mode.Throughput))
@OutputTimeUnit(TimeUnit.SECONDS)
class RouteBenchmark {
  @Benchmark
  def matchSimpleRoute(): Unit = {
    val route = Route.literal("/users") / "profile"
    route.matches(Path("/users/profile"))
  }

  @Benchmark
  def matchComplexRoute(): Unit = {
    val route = Route.literal("/api") / "users" / Route.var[String]("id") / "posts"
    route.matches(Path("/api/users/123/posts"))
  }
}
