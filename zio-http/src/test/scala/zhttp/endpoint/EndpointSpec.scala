package zhttp.endpoint

import zhttp.http._
import zio.UIO
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assert, assertM}

object EndpointSpec extends DefaultRunnableSpec {
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
      }
  } +
    suite("to") {
      test("endpoint doesn't match") {
        val app = Method.GET / "a" to { _ => Response.ok }
        assertM(app(Request(url = URL(!! / "b"))).flip)(isNone)
      } +
        test("endpoint with effect doesn't match") {
          val app = Method.GET / "a" to { _ => UIO(Response.ok) }
          assertM(app(Request(url = URL(!! / "b"))).flip)(isNone)
        } +
        test("endpoint matches") {
          val app = Method.GET / "a" to { _ => Response.ok }
          assertM(app(Request(url = URL(!! / "a"))).map(_.status))(equalTo(Status.OK))
        } +
        test("endpoint with effect matches") {
          val app = Method.GET / "a" to { _ => UIO(Response.ok) }
          assertM(app(Request(url = URL(!! / "a"))).map(_.status))(equalTo(Status.OK))
        }
    }
}
