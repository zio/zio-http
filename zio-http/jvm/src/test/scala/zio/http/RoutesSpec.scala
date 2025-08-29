/*
 * Copyright Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http

import zio.test._

import zio.http.codec.{PathCodec, SegmentCodec}

object RoutesSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  def spec = suite("RoutesSpec")(
    test("empty not found") {
      val app = Routes.empty

      for {
        result <- app.run()
      } yield assertTrue(extractStatus(result) == Status.NotFound)
    },
    test("compose empty not found") {
      val app = Routes.empty ++ Routes.empty

      for {
        result <- app.run()
      } yield assertTrue(extractStatus(result) == Status.NotFound)
    },
    test("run identity") {
      val body = Body.fromString("foo")

      val app = handler { (req: Request) =>
        Response(body = req.body)
      }

      for {
        result <- app.runZIO(Request(body = body))
      } yield assertTrue(result.body == body)
    },
    test("routes with different path parameter arities should all be handled") {
      val one    = Method.GET / string("first") -> Handler.ok
      val getone = Request.get("/1")

      val two    = Method.GET / string("prefix") / string("second") -> Handler.internalServerError
      val gettwo = Request.get("/2/two")

      val onetwo = Routes(one, two)
      val twoone = Routes(two, one)

      for {
        onetwoone <- onetwo.runZIO(getone)
        onetwotwo <- onetwo.runZIO(gettwo)
        twooneone <- twoone.runZIO(getone)
        twoonetwo <- twoone.runZIO(gettwo)
      } yield {
        assertTrue(
          extractStatus(onetwoone) == Status.Ok,
          extractStatus(onetwotwo) == Status.InternalServerError,
          extractStatus(twooneone) == Status.Ok,
          extractStatus(twoonetwo) == Status.InternalServerError,
        )
      }
    },
    test("nest routes") {
      import PathCodec._
      import zio._
      val routes = literal("to") / Routes(
        Method.GET / "other"             -> Handler.ok,
        Method.GET / "do" / string("id") -> handler { (id: String, _: Request) => Response.text(s"GET /to/do/${id}") },
      )

      for {
        nested1 <- routes.run(path = Path.root / "to" / "do" / "123")
        nested2 <- routes.run(path = Path.root / "to" / "other")
        former1 <- routes.run(path = Path.root / "other")
        former2 <- routes.run(path = Path.root / "do")
      } yield assertTrue(
        nested1.status == Status.Ok,
        nested2.status == Status.Ok,
        former1.status == Status.NotFound,
        former2.status == Status.NotFound,
      )
    },
    test("alternative path segments") {
      val app = Routes(
        Method.GET / anyOf("foo", "bar", "baz") -> Handler.ok,
      )

      for {
        foo <- app.runZIO(Request.get("/foo"))
        bar <- app.runZIO(Request.get("/bar"))
        baz <- app.runZIO(Request.get("/baz"))
        box <- app.runZIO(Request.get("/box"))
      } yield {
        assertTrue(
          extractStatus(foo) == Status.Ok,
          extractStatus(bar) == Status.Ok,
          extractStatus(baz) == Status.Ok,
          extractStatus(box) == Status.NotFound,
        )
      }
    },
    test("overlapping routes with different segment types") {
      val app = Routes(
        Method.GET / "foo" / string("id")                                      -> Handler.status(Status.NoContent),
        Method.GET / "foo" / string("id")                                      -> Handler.ok,
        Method.GET / "foo" / (SegmentCodec.literal("prefix") ~ string("rest")) -> Handler.ok,
        Method.GET / "foo" / int("id")                                         -> Handler.ok,
      )

      for {
        stringId     <- app.runZIO(Request.get("/foo/123"))
        stringPrefix <- app.runZIO(Request.get("/foo/prefix123"))
        intId        <- app.runZIO(Request.get("/foo/123"))
        notFound     <- app.runZIO(Request.get("/foo/123/456"))
        logs         <- ZTestLogger.logOutput.map { logs => logs.map(_.message()) }
      } yield {
        println(logs)
        assertTrue(
          logs.contains("Duplicate routes detected:\nGET /foo/{id}\nThe last route of each path will be used."),
          extractStatus(stringId) == Status.Ok,
          extractStatus(stringPrefix) == Status.Ok,
          extractStatus(intId) == Status.Ok,
          extractStatus(notFound) == Status.NotFound,
        )
      }
    },
    test("specific route should take precedence over trailing route") {
      val routes = Routes(
        Method.GET / string("test") / "expectedA" -> handler { (test: String, req: Request) =>
          Response.text(s"expectedA: $test")
        },
        Method.GET / trailing -> handler { (path: Path, req: Request) =>
          Response.text(s"Remainder: $path")
        },
      )

      for {
        result <- routes.runZIO(Request.get("/value/expectedA"))
        body   <- result.body.asString
      } yield {
        // The specific route should take precedence over the trailing route
        assertTrue(body == "expectedA: value")
      }
    },
    test("route order should be preserved for non-duplicates") {
      val routes = Routes(
        Method.GET / "first" -> handler { (_: Request) =>
          Response.text("first")
        },
        Method.GET / trailing -> handler { (path: Path, req: Request) =>
          Response.text(s"trailing: $path")
        },
        Method.GET / "second" -> handler { (_: Request) =>
          Response.text("second")
        },
      )

      for {
        firstResult  <- routes.runZIO(Request.get("/first"))
        firstBody    <- firstResult.body.asString
        secondResult <- routes.runZIO(Request.get("/second"))
        secondBody   <- secondResult.body.asString
        otherResult  <- routes.runZIO(Request.get("/other"))
        otherBody    <- otherResult.body.asString
      } yield {
        assertTrue(
          firstBody == "first",
          secondBody == "second", 
          otherBody == "trailing: /other"
        )
      }
    },
  )
}
