package zhttp.endpoint

import zhttp.http._
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert}

object EndpointSpec extends DefaultRunnableSpec {
  def spec = suite("Route") {
    test("match method") {
      val route   = Endpoint.fromMethod(Method.GET)
      val request = Request(Method.GET)
      assert(route.parse(request))(isSome(equalTo(())))
    }
    test("not match method") {
      val route   = Endpoint.fromMethod(Method.POST)
      val request = Request(Method.GET)
      assert(route.parse(request))(isNone)
    } +
      test("match method and string") {
        val route   = Endpoint.fromMethod(Method.GET) / "a"
        val request = Request(Method.GET, URL(Path("a")))
        assert(route.parse(request))(isSome(equalTo(())))
      } +
      test("match method and not string") {
        val route   = Endpoint.fromMethod(Method.GET) / "a"
        val request = Request(Method.GET, URL(Path("b")))
        assert(route.parse(request))(isNone)
      }
  } + suite("Path") {
    test("Route[Int]") {
      val route = Endpoint.fromMethod(Method.GET) / Endpoint[Int]
      assert(route.parse(!! / "1"))(isSome(equalTo(1))) && assert(route.parse(!! / "a"))(isNone)
    } +
      test("Route[String]") {
        val route = Endpoint.fromMethod(Method.GET) / Endpoint[String]
        assert(route.parse(!! / "a"))(isSome(equalTo("a")))
      } +
      test("Route[Boolean]") {
        val route = Endpoint.fromMethod(Method.GET) / Endpoint[Boolean]
        assert(route.parse(!! / "True"))(isSome(isTrue)) &&
        assert(route.parse(!! / "False"))(isSome(isFalse)) &&
        assert(route.parse(!! / "a"))(isNone) &&
        assert(route.parse(!! / "1"))(isNone)
      } +
      test("Route[Int] / Route[Int]") {
        val route = Endpoint.fromMethod(Method.GET) / Endpoint[Int] / Endpoint[Int]
        assert(route.parse(!! / "1" / "2"))(isSome(equalTo((1, 2)))) &&
        assert(route.parse(!! / "1" / "b"))(isNone) &&
        assert(route.parse(!! / "b" / "1"))(isNone) &&
        assert(route.parse(!! / "1"))(isNone) &&
        assert(route.parse(!!))(isNone)
      } +
      test("Route[Int] / c") {
        val route = Endpoint.fromMethod(Method.GET) / Endpoint[Int] / "c"
        assert(route.parse(!! / "1" / "c"))(isSome(equalTo(1))) &&
        assert(route.parse(!! / "1"))(isNone) &&
        assert(route.parse(!! / "c"))(isNone)
      } +
      test("Route[Int] / c") {
        val route = Endpoint.fromMethod(Method.GET) / Endpoint[Int] / "c"
        assert(route.parse(!! / "1" / "c"))(isSome(equalTo(1))) &&
        assert(route.parse(!! / "1"))(isNone) &&
        assert(route.parse(!! / "c"))(isNone)
      }
  }
}
