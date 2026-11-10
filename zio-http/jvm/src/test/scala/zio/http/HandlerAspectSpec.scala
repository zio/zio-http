package zio.http

import zio._
import zio.test._

object HandlerAspectSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] =
    suite("HandlerAspect")(
      test("HandlerAspect with context can eliminate environment type") {
        val handler0 = handler((_: Request) => ZIO.serviceWith[Int](i => Response.text(i.toString))) @@
          HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, req.headers.size)))
        for {
          response   <- handler0(Request(headers = Headers("accept", "*")))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1")
      },
      test("HandlerAspect preserves path parameters and context (#3141)") {
        val aspect = HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, 7)))
        val routes = Routes(
          Method.DELETE / string("message") -> handler { (message: String, _: Request) =>
            ZIO.serviceWith[Int](i => Response.text(s"$message $i"))
          } @@ aspect,
        )
        for {
          response <- routes.runZIO(Request.delete(url"/twenty"))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, body == "twenty 7")
      },
      test("HandlerAspect updates the request while preserving multiple path parameters") {
        val aspect = HandlerAspect.updateRequest(_.withBody(Body.fromString("updated"))) ++
          HandlerAspect.updateResponse(_.status(Status.Accepted))
        val routes = Routes(
          Method.GET / string("name") / int("age") -> handler { (name: String, age: Int, req: Request) =>
            req.body.asString.orDie.map(body => Response.text(s"$name $age $body"))
          } @@ aspect,
        )
        for {
          response <- routes.runZIO(Request.get(url"/Alice/42"))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Accepted, body == "Alice 42 updated")
      },
      test("context aspects preserve tuple input, modified requests and the remaining environment") {
        val aspect: HandlerAspect[String, Int] = HandlerAspect.interceptIncomingHandler {
          handler { (req: Request) =>
            ZIO.serviceWith[String](body => (req.withBody(Body.fromString(body)), 7))
          }
        }
        val wrapped                            = handler { (name: String, age: Int, req: Request) =>
          withContext { (enabled: Boolean, i: Int) =>
            req.body.asString.orDie.map(body => Response.text(s"$name $age $enabled $i $body"))
          }
        }.@@[Boolean](aspect)
        val routes                             = Routes(Method.GET / string("name") / int("age") -> wrapped)
        for {
          response <- ZIO
            .scoped(routes.runZIO(Request.get(url"/Alice/42")))
            .provideEnvironment(ZEnvironment(true) ++ ZEnvironment("updated"))
          body     <- response.body.asString
        } yield assertTrue(response.status == Status.Ok, body == "Alice 42 true 7 updated")
      },
      test("tuple input is preserved across chained aspects and concurrent requests") {
        val aspect = HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, 7)))
        val routes = Routes(
          Method.GET / string("message") -> (handler { (message: String, req: Request) =>
            ZIO.serviceWith[Int](i => Response.text(s"$message $i ${req.method}"))
          } @@ aspect @@ HandlerAspect.updateRequest(_.copy(method = Method.POST))),
        )
        for {
          bodies <- ZIO.foreachPar(List("one", "two")) { message =>
            routes.runZIO(Request.get(URL.root / message)).flatMap(_.body.asString)
          }
        } yield assertTrue(bodies == List("one 7 POST", "two 7 POST"))
      },
      test("tuple handlers retain scoped resources when applying an aspect") {
        for {
          released <- Ref.make(false)
          wrapped = Handler.scoped[Int] {
            handler { (message: String, _: Request) =>
              ZIO.addFinalizer(released.set(true)) *>
                ZIO.serviceWith[Int](i => Response.text(s"$message $i"))
            }
          } @@ HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, 7)))
          response <- ZIO.scoped(wrapped(("scoped", Request.get(url"/"))))
          body     <- response.body.asString
          done     <- released.get
        } yield assertTrue(body == "scoped 7", done)
      },
      test("HandlerAspect supports handlers accepting a supertype of Request") {
        val wrapped = Handler.fromFunction[AnyRef](_ => Response.ok) @@ HandlerAspect.identity
        for {
          response <- wrapped(Request.get(url"/"))
        } yield assertTrue(response.status == Status.Ok)
      },
      test("HandlerAspect rejects inputs without a request") {
        assertZIO(typeCheck("""Handler.fromFunction[Int](_ => Response.ok) @@ HandlerAspect.identity"""))(
          Assertion.isLeft,
        )
      },
      // format: off
      test("HandlerAspect with context can eliminate environment type partially") {
        val handlerAspect = HandlerAspect.interceptIncomingHandler(handler((req: Request) => (req, req.headers.size)))
        val handler0 = handler { (_: Request) =>
          withContext((_: Boolean, i: Int) => Response.text(i.toString))
          //leftover type is only needed in Scala 2
          //can't be infix because of Scala 3
        }.@@[Boolean](handlerAspect)
        for {
          response   <- ZIO.scoped(handler0(Request(headers = Headers("accept", "*")))).provideEnvironment(ZEnvironment(true))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1")
      },
      test("HandlerAspect with context can eliminate environment type partially while requiring an additional environment") {
        val handlerAspect: HandlerAspect[String, Int] = HandlerAspect.interceptIncomingHandler {
          handler((req: Request) => withContext((s: String) => (req.withBody(Body.fromString(s)), req.headers.size)))
        }
        val handler0: Handler[Boolean with String, Response, Request, Response] = handler { (r: Request) =>
          ZIO.service[Boolean] *> withContext{ (i: Int) =>
            for {
              body <- r.body.asString.orDie
            } yield Response.text(s"$i $body")
          }
          //leftover type is only needed in Scala 2
          //can't be infix because of Scala 3
        }.@@[Boolean](handlerAspect)
        for {
          response   <- ZIO.scoped(handler0(Request(headers = Headers("accept", "*")))).provideEnvironment(ZEnvironment(true) ++ ZEnvironment("test"))
          bodyString <- response.body.asString
        } yield assertTrue(bodyString == "1 test")
      },
      // format: on
    )
}
