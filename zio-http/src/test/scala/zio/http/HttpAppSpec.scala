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

import zio._
import zio.test.Assertion._
import zio.test._

object HttpAppSpec extends ZIOSpecDefault {
  def extractStatus(response: Response): Status = response.status

  def spec = suite("HttpAppSpec")(
    test("empty not found") {
      val app = HttpApp.empty

      for {
        result <- app.run()
      } yield assertTrue(extractStatus(result) == Status.NotFound)
    },
    test("compose empty not found") {
      val app = HttpApp.empty ++ HttpApp.empty

      for {
        result <- app.run()
      } yield assertTrue(extractStatus(result) == Status.NotFound)
    },
    test("run identity") {
      val body = Body.fromString("foo")

      val app = handler { (req: Request) =>
        Response(body = req.body)
      }.toHttpApp

      for {
        result <- app.runZIO(Request(body = body))
      } yield assertTrue(result.body == body)
    },
  )
}
