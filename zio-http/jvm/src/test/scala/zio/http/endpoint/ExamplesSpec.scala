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

package zio.http.endpoint

import java.time.Instant

import zio._
import zio.test._

import zio.stream.ZStream

import zio.schema.annotation.validate
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.endpoint._
import zio.http.forms.Fixtures.formField

object ExamplesSpec extends ZIOHttpSpec {
  def spec = suite("ExamplesSpec")(
    test("add examples to endpoint") {
      check(Gen.alphaNumericString, Gen.alphaNumericString) { (repo1, repo2) =>
        val endpoint             = Endpoint(GET / "repos" / string("org"))
          .out[String]
          .examplesIn("org" -> "zio")
          .examplesOut("repos" -> s"all, zio, repos, $repo1, $repo2")
        val endpoint2            =
          Endpoint(GET / "repos" / string("org") / string("repo"))
            .out[String]
            .examplesIn(
              "org/repo1" -> ("zio", "http"),
              "org/repo2" -> ("zio", "zio"),
              "org/repo3" -> ("zio", repo1),
              "org/repo4" -> ("zio", repo2),
            )
            .examplesOut("repos" -> s"zio, http, $repo1, $repo2")
        val inExamples1          = endpoint.examplesIn
        val expectedInExamples1  = Map("org" -> "zio")
        val outExamples1         = endpoint.examplesOut
        val expectedOutExamples1 = Map("repos" -> s"all, zio, repos, $repo1, $repo2")
        val inExamples2          = endpoint2.examplesIn
        val expectedInExamples2  = Map(
          "org/repo1" -> ("zio", "http"),
          "org/repo2" -> ("zio", "zio"),
          "org/repo3" -> ("zio", repo1),
          "org/repo4" -> ("zio", repo2),
        )
        val outExamples2         = endpoint2.examplesOut
        val expectedOutExamples2 = Map("repos" -> s"zio, http, $repo1, $repo2")

        assertTrue(
          inExamples1 == expectedInExamples1,
          outExamples1 == expectedOutExamples1,
          inExamples2 == expectedInExamples2,
          outExamples2 == expectedOutExamples2,
        )
      }
    },
  )
}
