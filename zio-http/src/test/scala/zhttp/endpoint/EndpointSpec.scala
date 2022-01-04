package zhttp.endpoint

import zhttp.http._
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert}

object EndpointSpec extends DefaultRunnableSpec with HExitAssertion {
  def spec = suite("Route") {
    test("match method") {
      val route   = Endpoint.fromMethod(Method.GET)
      val request = Request(Method.GET)
      assert(route.extract(request))(isSome(equalTo(())))
    }
    test("not match method") {
      val route   = Endpoint.fromMethod(Method.POST)
      val request = Request(Method.GET)
      assert(route.extract(request))(isNone)
    } +
      test("match method and string") {
        val route   = Method.GET / "a"
        val request = Request(Method.GET, URL(Path("a")))
        assert(route.extract(request))(isSome(equalTo(())))
      } +
      test("match method and not string") {
        val route   = Method.GET / "a"
        val request = Request(Method.GET, URL(Path("b")))
        assert(route.extract(request))(isNone)
      }
  } + suite("Path") {
    test("Route[Int]") {
      val route = Method.GET / *[Int]
      assert(route.extract(!! / "1"))(isSome(equalTo(1))) && assert(route.extract(!! / "a"))(isNone)
    } +
      test("Route[String]") {
        val route = Method.GET / *[String]
        assert(route.extract(!! / "a"))(isSome(equalTo("a")))
      } +
      test("Route[Boolean]") {
        val route = Method.GET / *[Boolean]
        assert(route.extract(!! / "True"))(isSome(isTrue)) &&
        assert(route.extract(!! / "False"))(isSome(isFalse)) &&
        assert(route.extract(!! / "a"))(isNone) &&
        assert(route.extract(!! / "1"))(isNone)
      } +
      test("Route[Int] / Route[Int]") {
        val route = Method.GET / *[Int] / *[Int]
        assert(route.extract(!! / "1" / "2"))(isSome(equalTo((1, 2)))) &&
        assert(route.extract(!! / "1" / "b"))(isNone) &&
        assert(route.extract(!! / "b" / "1"))(isNone) &&
        assert(route.extract(!! / "1"))(isNone) &&
        assert(route.extract(!!))(isNone)
      } +
      test("Route[Int] / c") {
        val route = Method.GET / *[Int] / "c"
        assert(route.extract(!! / "1" / "c"))(isSome(equalTo(1))) &&
        assert(route.extract(!! / "1"))(isNone) &&
        assert(route.extract(!! / "c"))(isNone)
      } +
      test("Route[Int] / c") {
        val route = Method.GET / *[Int] / "c"
        assert(route.extract(!! / "1" / "c"))(isSome(equalTo(1))) &&
        assert(route.extract(!! / "1"))(isNone) &&
        assert(route.extract(!! / "c"))(isNone)
      } +
      test("Failure to find endpoint should return HExit.empty") {
        val a      = Method.GET / "a" / *[Int] to { _ => Response.ok }
        val actual = a.execute(Request(Method.GET, URL(Path("/b/2"))))
        assert(actual)(equalTo(HExit.empty))
      } // +
//      testM("Failure to find endpoint with effect should return HExit.empty") {
//        val a      = Method.GET / "a" / *[Int] to { _ => zio.UIO(Response.ok) }
//        val actual = a.execute(Request(Method.GET, URL(Path("/b/2")))).toEffect
//        zio.test.assertM(actual)(equalTo(HExit.empty))
//      }

  }
}
