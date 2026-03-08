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
    test("embedded route should not match root path - issue #3609") {
      import PathCodec._
      val routes = literal("api") / Routes(
        Method.GET / "" -> handler(Response.text("from api route")),
      ) ++ Routes(
        Method.GET / "" -> handler(Response.text("from / route")),
      )

      for {
        rootResponse <- routes.run(path = Path.root)
        rootBody     <- rootResponse.body.asString
        apiResponse  <- routes.run(path = Path.root / "api")
        apiBody      <- apiResponse.body.asString
      } yield assertTrue(
        rootResponse.status == Status.Ok,
        rootBody == "from / route",
        apiResponse.status == Status.Ok,
        apiBody == "from api route",
      )
    },
    test("trailing path matchers are checked last regardless of registration order") {
      // Test case 1: Trailing route registered first
      val appTrailingFirst = Routes(
        Method.GET / "api" / string("version") / SegmentCodec.trailing -> handler(
          (version: String, path: Path, _: Request) => Response.text(s"trailing-first-v$version-${path.encode}"),
        ),
        Method.GET / "api" / string("version") / "users"               -> handler((version: String, _: Request) =>
          Response.text(s"users-v$version"),
        ),
      )

      // Test case 2: Trailing route registered in the middle
      val appTrailingMiddle = Routes(
        Method.GET / "api" / string("version") / "users"               -> handler((version: String, _: Request) =>
          Response.text(s"users-v$version"),
        ),
        Method.GET / "api" / string("version") / SegmentCodec.trailing -> handler(
          (version: String, path: Path, _: Request) => Response.text(s"trailing-middle-v$version-${path.encode}"),
        ),
        Method.GET / "api" / string("version") / "posts"               -> handler((version: String, _: Request) =>
          Response.text(s"posts-v$version"),
        ),
      )

      // Test case 3: Trailing route registered last
      val appTrailingLast = Routes(
        Method.GET / "api" / string("version") / "users"               -> handler((version: String, _: Request) =>
          Response.text(s"users-v$version"),
        ),
        Method.GET / "api" / string("version") / SegmentCodec.trailing -> handler(
          (version: String, path: Path, _: Request) => Response.text(s"trailing-last-v$version-${path.encode}"),
        ),
      )

      // Test case 4: Multiple trailing routes (should use the last one)
      val appMultipleTrailing = Routes(
        Method.GET / "api" / string("version") / SegmentCodec.trailing -> handler(
          (version: String, path: Path, _: Request) => Response.text(s"trailing-first-v$version-${path.encode}"),
        ),
        Method.GET / "api" / string("version") / "users"               -> handler((version: String, _: Request) =>
          Response.text(s"users-v$version"),
        ),
        Method.GET / "api" / string("version") / SegmentCodec.trailing -> handler(
          (version: String, path: Path, _: Request) => Response.text(s"trailing-second-v$version-${path.encode}"),
        ),
      )

      for {
        // Test exact matches - these should always match specific routes
        usersFirst    <- appTrailingFirst.runZIO(Request.get("/api/v1/users"))
        trailingFirst <- appTrailingFirst.runZIO(Request.get("/api/v1/unknown/path"))

        usersMiddle    <- appTrailingMiddle.runZIO(Request.get("/api/v2/users"))
        postsMiddle    <- appTrailingMiddle.runZIO(Request.get("/api/v2/posts"))
        trailingMiddle <- appTrailingMiddle.runZIO(Request.get("/api/v2/unknown/path"))

        usersLast    <- appTrailingLast.runZIO(Request.get("/api/v3/users"))
        trailingLast <- appTrailingLast.runZIO(Request.get("/api/v3/unknown/path"))

        usersMultiple    <- appMultipleTrailing.runZIO(Request.get("/api/v4/users"))
        trailingMultiple <- appMultipleTrailing.runZIO(Request.get("/api/v4/unknown/path"))

        usersFirstBody    <- usersFirst.body.asString
        trailingFirstBody <- trailingFirst.body.asString

        usersMiddleBody    <- usersMiddle.body.asString
        postsMiddleBody    <- postsMiddle.body.asString
        trailingMiddleBody <- trailingMiddle.body.asString

        usersLastBody    <- usersLast.body.asString
        trailingLastBody <- trailingLast.body.asString

        usersMultipleBody    <- usersMultiple.body.asString
        trailingMultipleBody <- trailingMultiple.body.asString

      } yield {
        assertTrue(
          // Specific routes should match correctly regardless of trailing route position
          usersFirstBody == "users-vv1",
          usersMiddleBody == "users-vv2",
          postsMiddleBody == "posts-vv2",
          usersLastBody == "users-vv3",
          usersMultipleBody == "users-vv4",

          // Trailing routes should capture both the version parameter and the remaining path
          trailingFirstBody == "trailing-first-vv1-unknown/path",
          trailingMiddleBody == "trailing-middle-vv2-unknown/path",
          trailingLastBody == "trailing-last-vv3-unknown/path",
          trailingMultipleBody == "trailing-second-vv4-unknown/path",

          // All responses should be successful
          extractStatus(usersFirst) == Status.Ok,
          extractStatus(trailingFirst) == Status.Ok,
          extractStatus(trailingMiddle) == Status.Ok,
          extractStatus(trailingLast) == Status.Ok,
          extractStatus(trailingMultiple) == Status.Ok,
        )
      }
    },
  )
}
