---
id: testing-http-apps
title: Testing HTTP Applications
---

Testing HTTP applications is a critical part of the development process. Utilizing the ZIO Test we can write first-class tests for our HTTP applications.

## ZIO Test

We have comprehensive documentation on [ZIO Test](https://zio.dev/reference/test/) which is worth reading to understand how to write tests using ZIO effects.

It is easy to test ZIO HTTP applications because we can think of `Routes` as a function of `Request => ZIO[R, Response, Response]`. By provide a `Request` to `Routes#runZIO` will output a `Response`. Without starting a server:

```scala mdoc:silent:reset
import zio.test._
import zio.test.Assertion.equalTo
import zio.http._

object ExampleSpec extends ZIOSpecDefault {

  def spec = suite("http")(
    test("should be ok") {
      val app = Handler.ok.toRoutes
      val req = Request.get(URL(Path.root))
      assertZIO(app.runZIO(req))(equalTo(Response.ok))
    }
  )
}
```

## ZIO HTTP Testkit

ZIO HTTP provides the `zio-http-testkit` package that includes a `TestClient` and a `TestServer`.

```scala
libraryDependencies += "dev.zio" %% "zio-test"         % "@ZIO_VERSION@"  % Test
libraryDependencies += "dev.zio" %% "zio-test-sbt"     % "@ZIO_VERSION@"  % Test
libraryDependencies += "dev.zio" %% "zio-http-testkit" % "@VERSION@" % Test
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

Now, based on the requirement we can use any of the following test utilities:

## TestClient

The `TestClient` allows us to write tests for our HTTP applications by defining the behavior of the client:

- `TestClient.addRequestResponse` - Adds an 1-1 mapping from a `Request` to a `Response` to the `TestClient`. 
- `TestClient.addRoute` and `addRoutes` - Add one or more `Route` or a single `Routes` instance to the `TestClient`.
- `TestClient.installSocketApp` - Installs a `WebSocketApp` to the `TestClient`.

After defining the behavior of the test client, we can use the `TestClient.layer` to provide the `TestClient` and `Client` to the test cases:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.test.{test, _}

object TestUsingTestClient extends ZIOSpecDefault {
  def spec = 
    test("hello world route and fallback") {
      for {
        client           <- ZIO.service[Client]
        _                <- TestClient.addRoutes {
          Routes(
            Method.GET / trailing          -> handler { Response.text("fallback") },
            Method.GET / "hello" / "world" -> handler { Response.text("Hey there!") },
          )
        }
        helloResponse    <- client.batched(Request.get(URL.root / "hello" / "world"))
        helloBody        <- helloResponse.body.asString
        fallbackResponse <- client.batched(Request.get(URL.root / "any"))
        fallbackBody     <- fallbackResponse.body.asString
      } yield assertTrue(helloBody == "Hey there!", fallbackBody == "fallback")
    }.provide(TestClient.layer)
}
```

## TestServer

The `TestServer` allows us to write tests for our HTTP applications by defining the behavior of the server:

- `TestServer.addRequestResponse` - Adds an 1-1 mapping from a `Request` to a `Response` to the `TestServer`. 
- `TestServer.addRoute` and `TestServer.addRoutes` - Add one or more `Route` or a single `Routes` instance to the `TestServer`. 

After defining the behavior of the test server, we can use the `TestServer.layer` to provide the `TestServer` to any test cases that require `Server`:

```scala mdoc:compile-only
import zio._
import zio.http._
import zio.http.netty.NettyConfig
import zio.http.netty.server.NettyDriver
import zio.test._

object TestServerExampleSpec extends ZIOSpecDefault {

  def spec = suite("test http app") {
    test("test hello and fallback routes") {
      for {
        client <- ZIO.service[Client]
        port   <- ZIO.serviceWithZIO[Server](_.port)
        testRequest = Request
          .get(url = URL.root.port(port))
          .addHeaders(Headers(Header.Accept(MediaType.text.`plain`)))
        _                <- TestServer.addRoutes {
          Routes(
            Method.GET / trailing          -> handler {
              Response.text("fallback")
            },
            Method.GET / "hello" / "world" -> handler {
              Response.text("Hey there!")
            },
          )
        }
        helloResponse    <- client.batched(Request.get(testRequest.url / "hello" / "world"))
        helloBody        <- helloResponse.body.asString
        fallbackResponse <- client.batched(Request.get(testRequest.url / "any"))
        fallbackBody     <- fallbackResponse.body.asString
      } yield assertTrue(helloBody == "Hey there!", fallbackBody == "fallback")
    }
  }.provide(
    TestServer.default,
    Client.default,
  )
}
```
