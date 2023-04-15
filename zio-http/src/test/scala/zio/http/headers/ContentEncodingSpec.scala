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

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}
import zio.{NonEmptyChunk, Scope}

import zio.http.Header.ContentEncoding
import zio.http.Header.ContentEncoding.Multiple
import zio.http.internal.HttpGen

object ContentEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ContentEncoding suite")(
    suite("ContentEncoding header value transformation should be symmetrical")(
      test("gen single value") {
        check(HttpGen.allowContentEncodingSingleValue) { value =>
          assertTrue(ContentEncoding.parse(ContentEncoding.render(value)) == Right(value))
        }
      },
      test("single value") {
        assertTrue(
          ContentEncoding.parse("br") == Right(ContentEncoding.Br),
          ContentEncoding.parse("compress") == Right(ContentEncoding.Compress),
          ContentEncoding.parse("deflate") == Right(ContentEncoding.Deflate),
          ContentEncoding.parse("deflate, br, compress") == Right(
            ContentEncoding.Multiple(
              NonEmptyChunk(ContentEncoding.Deflate, ContentEncoding.Br, ContentEncoding.Compress),
            ),
          ),
          ContentEncoding.parse("garbage").isLeft,
        )

      },
      test("edge cases") {
        assertTrue(
          ContentEncoding
            .parse(" ")
            .isLeft,
          ContentEncoding.parse(
            ContentEncoding.render(
              Multiple(
                NonEmptyChunk(ContentEncoding.Deflate, ContentEncoding.Br, ContentEncoding.Compress),
              ),
            ),
          ) == Right(
            ContentEncoding.Multiple(
              NonEmptyChunk(ContentEncoding.Deflate, ContentEncoding.Br, ContentEncoding.Compress),
            ),
          ),
          ContentEncoding.parse(
            ContentEncoding.render(
              Multiple(
                NonEmptyChunk(ContentEncoding.Deflate),
              ),
            ),
          ) == Right(ContentEncoding.Deflate),
        )
      },
    ),
  )
}
