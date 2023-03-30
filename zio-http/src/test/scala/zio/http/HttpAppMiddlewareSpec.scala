/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

import zio.http.model.Method

object HttpAppMiddlewareSpec extends ZIOSpecDefault with ExitAssertion {

  def spec: Spec[Any, Any] =
    suite("HttpAppMiddleware")(
      test("combine") {
        for {
          ref <- Ref.make(0)
          mid1 = HttpAppMiddleware.runBefore(ref.update(_ + 1))
          mid2 = HttpAppMiddleware.runBefore(ref.update(_ + 2))
          app1 = Handler.ok @@ mid1 @@ mid2
          app2 = Handler.ok @@ (mid1 ++ mid2)
          _       <- app1.runZIO(Request.get(URL.root))
          result1 <- ref.get
          _       <- app2.runZIO(Request.get(URL.root))
          result2 <- ref.get
        } yield assertTrue(result1 == 3, result2 == 6)
      },
      test("runBefore") {
        val mid = HttpAppMiddleware.runBefore(Console.printLine("A"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("B").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      },
      test("runAfter") {
        val mid = HttpAppMiddleware.runAfter(Console.printLine("B"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("A").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n")))
      },
      test("runBefore and runAfter") {
        val mid =
          HttpAppMiddleware.runBefore(Console.printLine("A")) ++ HttpAppMiddleware.runAfter(Console.printLine("C"))
        val app = Handler.fromFunctionZIO((_: Request) => Console.printLine("B").as(Response.ok)) @@ mid
        assertZIO(app.runZIO(Request.get(URL.root)) *> TestConsole.output)(equalTo(Vector("A\n", "B\n", "C\n")))
      },
      test("when") {
        for {
          ref <- Ref.make(0)
          mid  = HttpAppMiddleware.runBefore(ref.update(_ + 1)).when(_.method == Method.GET)
          app1 = Handler.ok @@ mid
          _       <- app1.runZIO(Request.get(URL.root))
          result1 <- ref.get
          _       <- app1.runZIO(Request.default(Method.HEAD, URL.root))
          result2 <- ref.get
        } yield assertTrue(result1 == 1, result2 == 1)
      },
      test("whenZIO") {
        for {
          ref <- Ref.make(0)
          mid  = HttpAppMiddleware.runBefore(ref.update(_ + 1)).whenZIO(req => ZIO.succeed(req.method == Method.GET))
          app1 = Handler.ok @@ mid
          _       <- app1.runZIO(Request.get(URL.root))
          result1 <- ref.get
          _       <- app1.runZIO(Request.default(Method.HEAD, URL.root))
          result2 <- ref.get
        } yield assertTrue(result1 == 1, result2 == 1)
      },
    )
}
