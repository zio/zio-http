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

package zio.http.model.headers.values

import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}
import zio.{Chunk, Scope}

import zio.http.internal.HttpGen
import zio.http.model.headers.values.ContentEncoding.Multiple

object ContentEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ContentEncoding suite")(
    suite("ContentEncoding header value transformation should be symmetrical")(
      test("gen single value") {
        check(HttpGen.allowContentEncodingSingleValue) { value =>
          assertTrue(ContentEncoding.toContentEncoding(ContentEncoding.fromContentEncoding(value)) == Right(value))
        }
      },
      test("single value") {
        assertTrue(ContentEncoding.toContentEncoding("br") == Right(ContentEncoding.Br)) &&
        assertTrue(ContentEncoding.toContentEncoding("compress") == Right(ContentEncoding.Compress)) &&
        assertTrue(ContentEncoding.toContentEncoding("deflate") == Right(ContentEncoding.Deflate)) &&
        assertTrue(
          ContentEncoding.toContentEncoding("deflate, br, compress") == Right(
            ContentEncoding.Multiple(
              Chunk(ContentEncoding.Deflate, ContentEncoding.Br, ContentEncoding.Compress),
            ),
          ),
        ) &&
        assertTrue(ContentEncoding.toContentEncoding("garbage").isLeft)

      },
      test("edge cases") {
        assertTrue(
          ContentEncoding
            .toContentEncoding(
              ContentEncoding.fromContentEncoding(Multiple(Chunk())),
            )
            .isLeft,
        ) &&
        assertTrue(
          ContentEncoding.toContentEncoding(
            ContentEncoding.fromContentEncoding(
              Multiple(
                Chunk(ContentEncoding.Deflate, ContentEncoding.Br, ContentEncoding.Compress),
              ),
            ),
          ) == Right(
            ContentEncoding.Multiple(
              Chunk(ContentEncoding.Deflate, ContentEncoding.Br, ContentEncoding.Compress),
            ),
          ),
        ) &&
        assertTrue(
          ContentEncoding.toContentEncoding(
            ContentEncoding.fromContentEncoding(
              Multiple(
                Chunk(ContentEncoding.Deflate),
              ),
            ),
          ) == Right(ContentEncoding.Deflate),
        )
      },
    ),
  )
}
