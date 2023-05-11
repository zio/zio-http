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

import java.net.ConnectException

import zio._
import zio.test.Assertion._
import zio.test.TestAspect.{sequential, timeout, withLiveClock}
import zio.test._

import zio.stream.ZStream

import zio.http.internal.{DynamicServer, HttpRunnableSpec, severTestLayer}

object ClientSpec extends HttpRunnableSpec {

  def clientSpec = suite("ClientSpec")(
    test("respond Ok") {
      val app = Handler.ok.toHttp.deploy.status.run()
      assertZIO(app)(equalTo(Status.Ok))
    },
    test("non empty content") {
      val app             = Handler.text("abc").toHttp
      val responseContent = app.deploy.body.run().flatMap(_.asChunk)
      assertZIO(responseContent)(isNonEmpty)
    },
    test("echo POST request content") {
      val app = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.toHttp
      val res = app.deploy.body.mapZIO(_.asString).run(method = Method.POST, body = Body.fromString("ZIO user"))
      assertZIO(res)(equalTo("ZIO user"))
    },
    test("empty content") {
      val app             = Http.empty
      val responseContent = app.deploy.body.run().flatMap(_.asString.map(_.length))
      assertZIO(responseContent)(equalTo(0))
    },
    test("text content") {
      val app             = Handler.text("zio user does not exist").toHttp
      val responseContent = app.deploy.body.mapZIO(_.asString).run()
      assertZIO(responseContent)(containsString("user"))
    },
    test("handle connection failure") {
      val res = Client.request("http://localhost:1").either
      assertZIO(res)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("streaming content to server") {
      val app    = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.toHttp
      val stream = ZStream.fromIterable(List("a", "b", "c"), chunkSize = 1)
      val res    = app.deploy.body
        .run(method = Method.POST, body = Body.fromStream(stream))
        .flatMap(_.asString)
      assertZIO(res)(equalTo("abc"))
    },
    test("no trailing slash for empty path") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <- Handler.ok.toHttp
          .deployAndRequest(c => (c @@ ZClientAspect.requestLogging()).get(""))
          .runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == baseURL)
    },
    test("trailing slash for explicit slash") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <- Handler.ok.toHttp
          .deployAndRequest(c => (c @@ ZClientAspect.requestLogging()).get("/"))
          .runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == s"$baseURL/")
    },
  )

  override def spec = {
    suite("Client") {
      serve(DynamicServer.app).as(List(clientSpec))
    }.provideSomeShared[Scope](DynamicServer.live, severTestLayer, Client.default) @@
      timeout(5 seconds) @@ sequential @@ withLiveClock
  }
}
