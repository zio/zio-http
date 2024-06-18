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

import zio.http.codec.PathCodec

object RoutesSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  def spec = suite("HttpAppSpec")(
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
      case object IdFormatError
      val routes = literal("to") / Routes(
        Method.GET / "other"             -> handler(ZIO.fail(IdFormatError)),
        Method.GET / "do" / string("id") -> handler { (id: String, _: Request) => Response.text(s"GET /to/do/${id}") },
      ).handleError { case IdFormatError =>
        Response.badRequest
      }
      routes
        .run(
          path = Path.root / "to" / "do" / "123",
        )
        .map(response => assertTrue(response.status == Status.Ok))
    },
  )
}
