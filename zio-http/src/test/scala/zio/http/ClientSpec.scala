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
      val app = Handler.ok.toHttpApp.deploy(Request()).map(_.status)
      assertZIO(app)(equalTo(Status.Ok))
    },
    test("non empty content") {
      val app             = Handler.text("abc").toHttpApp
      val responseContent = app.deploy(Request()).flatMap(_.body.asChunk)
      assertZIO(responseContent)(isNonEmpty)
    },
    test("echo POST request content") {
      val app = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.sandbox.toHttpApp
      val res = app.deploy(Request(method = Method.POST, body = Body.fromString("ZIO user"))).flatMap(_.body.asString)
      assertZIO(res)(equalTo("ZIO user"))
    },
    test("empty content") {
      val app             = HttpApp.empty
      val responseContent = app.deploy(Request()).flatMap(_.body.asString.map(_.length))
      assertZIO(responseContent)(equalTo(0))
    },
    test("text content") {
      val app             = Handler.text("zio user does not exist").toHttpApp
      val responseContent = app.deploy(Request()).flatMap(_.body.asString)
      assertZIO(responseContent)(containsString("user"))
    },
    test("handle connection failure") {
      val url = URL.decode("http://localhost:1").toOption.get

      val res = ZClient.request(Request.get(url)).either
      assertZIO(res)(isLeft(isSubtype[ConnectException](anything)))
    },
    test("streaming content to server") {
      val app    = Handler.fromFunctionZIO[Request] { req => req.body.asString.map(Response.text(_)) }.sandbox.toHttpApp
      val stream = ZStream.fromIterable(List("a", "b", "c"), chunkSize = 1)
      val res    = app
        .deploy(Request(method = Method.POST, body = Body.fromStream(stream)))
        .flatMap(_.body.asString)
      assertZIO(res)(equalTo("abc"))
    },
    test("no trailing slash for empty path") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <- Handler.ok.toHttpApp
          .deployAndRequest(c => (c @@ ZClientAspect.requestLogging()).get(""))
          .runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == baseURL)
    },
    test("trailing slash for explicit slash") {
      for {
        baseURL   <- DynamicServer.httpURL
        _         <- Handler.ok.toHttpApp
          .deployAndRequest(c => (c @@ ZClientAspect.requestLogging()).get("/"))
          .runZIO(())
        loggedUrl <- ZTestLogger.logOutput.map(_.collectFirst { case m => m.annotations("url") }.mkString)
      } yield assertTrue(loggedUrl == s"$baseURL/")
    },
  )

  override def spec = {
    suite("Client") {
      serve.as(List(clientSpec))
    }.provideSomeShared[Scope](DynamicServer.live, severTestLayer, Client.default) @@
      timeout(5 seconds) @@ sequential @@ withLiveClock
  }
}
