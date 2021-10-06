package zhttp.experiment
import zhttp.http._
import zio.test.Assertion._
import zio.test._

object RouteSpec extends DefaultRunnableSpec {
  def spec = suite("Route") {
    test("match method") {
      val route   = Route.get
      val request = Request(Method.GET)
      assert(route(request))(isSome(equalTo(())))
    }
    test("not match method") {
      val route   = Route.post
      val request = Request(Method.GET)
      assert(route(request))(isNone)
    } +
      test("match method and string") {
        val route   = Route.get / "a"
        val request = Request(Method.GET, URL(Path("a")))
        assert(route(request))(isSome(equalTo(())))
      } +
      test("match method and not string") {
        val route   = Route.get / "a"
        val request = Request(Method.GET, URL(Path("b")))
        assert(route(request))(isNone)
      }
  } + suite("Path") {
    test("Route[Int]") {
      val route = Route.get / Route[Int]
      assert(route(!! / "1"))(isSome(equalTo(1))) && assert(route(!! / "a"))(isNone)
    } +
      test("Route[String]") {
        val route = Route.get / Route[String]
        assert(route(!! / "a"))(isSome(equalTo("a")))
      } +
      test("Route[Boolean]") {
        val route = Route.get / Route[Boolean]
        assert(route(!! / "True"))(isSome(isTrue)) &&
        assert(route(!! / "False"))(isSome(isFalse)) &&
        assert(route(!! / "a"))(isNone) &&
        assert(route(!! / "1"))(isNone)
      } +
      test("Route[Int] / Route[Int]") {
        val route = Route.get / Route[Int] / Route[Int]
        assert(route(!! / "1" / "2"))(isSome(equalTo((1, 2)))) &&
        assert(route(!! / "1" / "b"))(isNone) &&
        assert(route(!! / "b" / "1"))(isNone) &&
        assert(route(!! / "1"))(isNone) &&
        assert(route(!!))(isNone)
      } +
      test("Route[Int] / c") {
        val route = Route.get / Route[Int] / "c"
        assert(route(!! / "1" / "c"))(isSome(equalTo(1))) &&
        assert(route(!! / "1"))(isNone) &&
        assert(route(!! / "c"))(isNone)
      }
  }
}
