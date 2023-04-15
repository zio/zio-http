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

package zio.http.headers

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{NonEmptyChunk, Scope}

import zio.http.Header.AccessControlAllowMethods
import zio.http.Method

object AccessControlAllowMethodsSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AccessControlAllowMethods suite")(
    test("AccessControlAllowMethods should be parsed correctly") {
      val accessControlAllowMethods       = AccessControlAllowMethods.Some(
        NonEmptyChunk(
          Method.GET,
          Method.POST,
          Method.PUT,
          Method.DELETE,
          Method.HEAD,
          Method.OPTIONS,
          Method.PATCH,
        ),
      )
      val accessControlAllowMethodsString = "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH"
      assertTrue(
        AccessControlAllowMethods
          .parse(accessControlAllowMethodsString) == Right(accessControlAllowMethods),
      )
    },
    test("AccessControlAllowMethods should be parsed correctly when * is used") {
      val accessControlAllowMethods       = AccessControlAllowMethods.All
      val accessControlAllowMethodsString = "*"
      assertTrue(
        AccessControlAllowMethods
          .parse(accessControlAllowMethodsString) == Right(accessControlAllowMethods),
      )
    },
    test("AccessControlAllowMethods should be parsed correctly when empty string is used") {
      val accessControlAllowMethods       = AccessControlAllowMethods.None
      val accessControlAllowMethodsString = ""
      assertTrue(
        AccessControlAllowMethods
          .parse(accessControlAllowMethodsString) == Right(accessControlAllowMethods),
      )
    },
    test("AccessControlAllowMethods should properly render NoMethodsAllowed value") {
      assertTrue(
        AccessControlAllowMethods.render(AccessControlAllowMethods.None) == "",
      )
    },
    test("AccessControlAllowMethods should properly render AllowAllMethods value") {
      assertTrue(
        AccessControlAllowMethods.render(AccessControlAllowMethods.All) == "*",
      )
    },
    test("AccessControlAllowMethods should properly render AllowMethods value") {
      val accessControlAllowMethods       = AccessControlAllowMethods.Some(
        NonEmptyChunk(
          Method.GET,
          Method.POST,
          Method.PUT,
          Method.DELETE,
          Method.HEAD,
          Method.OPTIONS,
          Method.PATCH,
        ),
      )
      val accessControlAllowMethodsString = "GET, POST, PUT, DELETE, HEAD, OPTIONS, PATCH"
      assertTrue(
        AccessControlAllowMethods.render(
          accessControlAllowMethods,
        ) == accessControlAllowMethodsString,
      )
    },
  )
}
