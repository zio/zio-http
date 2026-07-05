package zio.http

import zio.test._

object ClientMiddlewareSpec extends ZIOSpecDefault {

  private val alwaysOk: Client = new Client {
    def send(request: Request): Response = Response.ok
  }

  private val alwaysNotFound: Client = new Client {
    def send(request: Request): Response = Response.notFound
  }

  private val testReq: Request = Request.get(URL.root)

  def spec = suite("ClientMiddleware")(
    suite("ClientMiddleware.identity")(
      test("returns the client unchanged (ok response preserved)") {
        val wrapped = ClientMiddleware.identity(alwaysOk)
        val resp    = wrapped.send(testReq)
        assertTrue(resp == Response.ok)
      },
      test("returns the client unchanged (notFound response preserved)") {
        val wrapped = ClientMiddleware.identity(alwaysNotFound)
        val resp    = wrapped.send(testReq)
        assertTrue(resp == Response.notFound)
      },
      test("identity applied twice is still identity") {
        val mid     = ClientMiddleware.identity.andThen(ClientMiddleware.identity)
        val wrapped = mid(alwaysOk)
        val resp    = wrapped.send(testReq)
        assertTrue(resp == Response.ok)
      },
    ),
    suite("ClientMiddleware.apply")(
      test("creates middleware from function") {
        var called = false
        val mid    = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = {
              called = true
              c.send(r)
            }
          }
        }
        mid(alwaysOk).send(testReq)
        assertTrue(called)
      },
    ),
    suite("ClientMiddleware.andThen")(
      test("m1.andThen(m2) applies both middlewares") {
        var m1Called = false
        var m2Called = false
        val m1       = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { m1Called = true; c.send(r) }
          }
        }
        val m2       = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { m2Called = true; c.send(r) }
          }
        }
        m1.andThen(m2)(alwaysOk).send(testReq)
        assertTrue(m1Called && m2Called)
      },
      test("m1.andThen(m2) calls m1 first then m2") {
        val callOrder = new scala.collection.mutable.ArrayBuffer[Int]()
        val m1        = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { callOrder += 1; c.send(r) }
          }
        }
        val m2        = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { callOrder += 2; c.send(r) }
          }
        }
        m1.andThen(m2)(alwaysOk).send(testReq)
        assertTrue(callOrder.toList == List(2, 1))
      },
      test("andThen preserves response") {
        val composed = ClientMiddleware.identity.andThen(ClientMiddleware.identity)
        val resp     = composed(alwaysOk).send(testReq)
        assertTrue(resp == Response.ok)
      },
    ),
    suite("Client.@@ operator")(
      test("client @@ middleware applies middleware") {
        var called = false
        val mid    = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { called = true; c.send(r) }
          }
        }
        val client = alwaysOk @@ mid
        client.send(testReq)
        assertTrue(called)
      },
      test("client @@ identity preserves response") {
        val client = alwaysOk @@ ClientMiddleware.identity
        val resp   = client.send(testReq)
        assertTrue(resp == Response.ok)
      },
      test("client @@ m1 @@ m2 chains correctly") {
        var m1Called = false
        var m2Called = false
        val m1       = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { m1Called = true; c.send(r) }
          }
        }
        val m2       = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { m2Called = true; c.send(r) }
          }
        }
        val client   = alwaysOk @@ m1 @@ m2
        client.send(testReq)
        assertTrue(m1Called && m2Called)
      },
    ),
    suite("Custom middleware wraps send calls")(
      test("middleware counts send invocations") {
        var callCount = 0
        val counter   = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = { callCount += 1; c.send(r) }
          }
        }
        val client    = alwaysOk @@ counter
        client.send(testReq)
        client.send(testReq)
        assertTrue(callCount == 2)
      },
      test("middleware can transform the response") {
        val transformer = ClientMiddleware { c =>
          new Client {
            def send(r: Request): Response = {
              val resp = c.send(r)
              Response(Status.Created, resp.headers, resp.body)
            }
          }
        }
        val client      = alwaysOk @@ transformer
        val resp        = client.send(testReq)
        assertTrue(resp.status == Status.Created)
      },
    ),
  )
}
