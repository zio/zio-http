---
id: testing-http-apps
title: Testing HTTP Applications
---

Testing HTTP applications is a critical part of the development process. Utilizing the ZIO Test we can write first-class tests for our HTTP applications.

## ZIO Test

We have comprehensive documentation on [ZIO Test](https://zio.dev/reference/test/) which is worth reading to understand how to write tests using ZIO effects.

It is easy to test ZIO HTTP applications because we can think of `HttpApp` as a function of `Request => ZIO[R, Response, Response]`. This means we can effortlessly provide a Request as input to the `HttpApp` and receive the corresponding Response as output using the runZIO method. By doing this we can test the behavior of the `HttpApp` in a controlled environment:

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

Also, ZIO HTTP provides a testkit called `zio-http-testkit` that includes `TestClient` and `TestServer` utilities which helps us to test our HTTP applications without the need for having a real live client and server instances.

```scala
libraryDependencies += "dev.zio" %% "zio-test"         % "@ZIO_VERSION@"  % Test
libraryDependencies += "dev.zio" %% "zio-test-sbt"     % "@ZIO_VERSION@"  % Test
libraryDependencies += "dev.zio" %% "zio-http-testkit" % "@VERSION@" % Test
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
```

Now, based on the requirement we can use any of the following test utilities:

## TestClient

Using the `TestClient` we can write tests for our HTTP applications without starting a live server instance.

Using following methods we can define the behavior of the `TestClient`:

- `TestClient.addRequestResponse` - Adds an exact 1-1 behavior. It takes a request and a response and returns a `ZIO[TestClient, Nothing, Unit]`.
- `TestClient.addRoute` and `addRouts` - Adds a route definition to handle requests that are submitted by test cases. It takes a `Route` or `Routes` and returns a `ZIO[R with TestClient, Nothing, Unit]`.
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
        helloResponse    <- client(Request.get(URL.root / "hello" / "world"))
        helloBody        <- helloResponse.body.asString
        fallbackResponse <- client(Request.get(URL.root / "any"))
        fallbackBody     <- fallbackResponse.body.asString
      } yield assertTrue(helloBody == "Hey there!", fallbackBody == "fallback")
    }.provide(TestClient.layer, Scope.default)
}
```

## TestServer

Using the `TestServer` we can write tests for our HTTP applications by starting a live server instance on the localhost.

Using the following methods we can define the behavior of the `TestServer`:

- `TestServer.addRequestResponse` - Adds an exact 1-1 behavior. It takes a request and a response and returns a `ZIO[TestServer, Nothing, Unit]`.
- `TestServer.addRoute` and `TestServer.addRouts` - Adds a route definition to handle requests that are submitted by test cases. It takes a `Route` or `Routes` and returns a `ZIO[R with TestServer, Nothing, Unit]`.
- `TestServer.install` - Installs a `HttpApp` to the `TestServer`.

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
        helloResponse    <- client(Request.get(testRequest.url / "hello" / "world"))
        helloBody        <- helloResponse.body.asString
        fallbackResponse <- client(Request.get(testRequest.url / "any"))
        fallbackBody     <- fallbackResponse.body.asString
      } yield assertTrue(helloBody == "Hey there!", fallbackBody == "fallback")
    }.provideSome[Client with Driver](TestServer.layer, Scope.default)
  }.provide(
    ZLayer.succeed(Server.Config.default.onAnyOpenPort),
    Client.default,
    NettyDriver.customized,
    ZLayer.succeed(NettyConfig.defaultWithFastShutdown),
  )
}
```
