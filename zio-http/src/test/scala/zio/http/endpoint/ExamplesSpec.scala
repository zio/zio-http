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
import zio.schema.codec.{DecodeError, JsonCodec}
import zio.schema.validation.Validation
import zio.schema.{DeriveSchema, Schema, StandardType}

import zio.http.Header.ContentType
import zio.http.Method._
import zio.http._
import zio.http.codec.HttpCodec.{query, queryInt}
import zio.http.codec._
import zio.http.endpoint.{MultipartSpec, NotFoundSpec, QueryParameterSpec}
import zio.http.forms.Fixtures.formField

object ExamplesSpec extends ZIOHttpSpec {
  def extractStatus(response: Response): Status = response.status

  case class NewPost(value: String)

  case class User(
    @validate(Validation.greaterThan(0))
    id: Int,
  )

  def spec = suite("ExamplesSpec")(
    test("add examples to endpoint") {
      val endpoint     = Endpoint(GET / "repos" / string("org"))
        .out[String]
        .examplesIn("repo" -> "zio")
        .examplesOut("foundRepos" -> "all, zio, repos")
      val endpoint2    =
        Endpoint(GET / "repos" / string("org") / string("repo"))
          .out[String]
          .examplesIn("repo and org" -> ("zio", "http"), "other repo and org" -> ("zio", "zio"))
          .examplesOut("repos" -> "zio, http")
      val inExamples1  = endpoint.examplesIn
      val outExamples1 = endpoint.examplesOut
      val inExamples2  = endpoint2.examplesIn
      val outExamples2 = endpoint2.examplesOut
      assertTrue(
        inExamples1 == Map("repo" -> "zio"),
        outExamples1 == Map("foundRepos" -> "all, zio, repos"),
        inExamples2 == Map("repo and org" -> ("zio", "http"), "other repo and org" -> ("zio", "zio")),
        outExamples2 == Map("repos" -> "zio, http"),
      )
    },
  )
}
