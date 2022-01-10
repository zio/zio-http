package zhttp.endpoint

import zhttp.http._
import zio.UIO
import zio.test.Assertion._
import zio.test.{DefaultRunnableSpec, assertTrue, assertM}

object EndpointSpec extends DefaultRunnableSpec {
  def spec = suite("Route") {
    test("match method") {
      val route = Endpoint.fromMethod(Method.GET)
      val request = Request(Method.GET)
      assertTrue(route.extract(request).contains(()))
    }
    test("not match method") {
      val route = Endpoint.fromMethod(Method.POST)
      val request = Request(Method.GET)
      assertTrue(route.extract(request).isEmpty)
    } +
      test("match method and string") {
        val route = Method.GET / "a"
        val request = Request(Method.GET, URL(Path("a")))
        assertTrue(route.extract(request).contains(()))
      } +
      test("match method and not string") {
        val route = Method.GET / "a"
        val request = Request(Method.GET, URL(Path("b")))
        assertTrue(route.extract(request).isEmpty)
      }
  } + suite("Path") {
    test("Route[Int]") {
      val route = Method.GET / *[Int]
      assertTrue(route.extract(!! / "1").contains(1)) && assertTrue(route.extract(!! / "a").isEmpty)
    } +
      test("Route[String]") {
        val route = Method.GET / *[String]
        assertTrue(route.extract(!! / "a").contains("a"))
      } +
      test("Route[Boolean]") {
        val route = Method.GET / *[Boolean]
        assertTrue(route.extract(!! / "True").contains(true)) &&
          assertTrue(route.extract(!! / "False").contains(false)) &&
          assertTrue(route.extract(!! / "a").isEmpty) &&
          assertTrue(route.extract(!! / "1").isEmpty)
      } +
      test("Route[Int] / Route[Int]") {
        val route = Method.GET / *[Int] / *[Int]
        assertTrue(route.extract(!! / "1" / "2").contains((1, 2))) &&
          assertTrue(route.extract(!! / "1" / "b").isEmpty) &&
          assertTrue(route.extract(!! / "b" / "1").isEmpty) &&
          assertTrue(route.extract(!! / "1").isEmpty) &&
          assertTrue(route.extract(!!).isEmpty)
      } +
      test("Route[Int] / c") {
        val route = Method.GET / *[Int] / "c"
        assertTrue(route.extract(!! / "1" / "c").contains(1)) &&
          assertTrue(route.extract(!! / "1").isEmpty) &&
          assertTrue(route.extract(!! / "c").isEmpty)
      } +
      test("Route[Int] / c") {
        val route = Method.GET / *[Int] / "c"
        assertTrue(route.extract(!! / "1" / "c").contains(1)) &&
          assertTrue(route.extract(!! / "1").isEmpty) &&
          assertTrue(route.extract(!! / "c").isEmpty)
      }
  } +
    suite("to") {
      testM("endpoint doesn't match") {
        val app = Method.GET / "a" to { _ => Response.ok }
        assertM(app(Request(url = URL(!! / "b"))).flip)(isNone)
      } +
        testM("endpoint with effect doesn't match") {
          val app = Method.GET / "a" to { _ => UIO(Response.ok) }
          assertM(app(Request(url = URL(!! / "b"))).flip)(isNone)
        } +
        testM("endpoint matches") {
          val app = Method.GET / "a" to { _ => Response.ok }
          assertM(app(Request(url = URL(!! / "a"))).map(_.status))(equalTo(Status.OK))
        } +
        testM("endpoint with effect matches") {
          val app = Method.GET / "a" to { _ => UIO(Response.ok) }
          assertM(app(Request(url = URL(!! / "a"))).map(_.status))(equalTo(Status.OK))
        }
    }
}
