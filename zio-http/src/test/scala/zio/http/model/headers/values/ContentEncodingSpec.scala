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
import zio.http.model.headers.values.ContentEncoding.{InvalidEncoding, MultipleEncodings}

object ContentEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ContentEncoding suite")(
    suite("ContentEncoding header value transformation should be symmetrical")(
      test("gen single value") {
        check(HttpGen.allowContentEncodingSingleValue) { value =>
          assertTrue(ContentEncoding.toContentEncoding(ContentEncoding.fromContentEncoding(value)) == value)
        }
      },
      test("single value") {
        assertTrue(ContentEncoding.toContentEncoding("br") == ContentEncoding.BrEncoding) &&
        assertTrue(ContentEncoding.toContentEncoding("compress") == ContentEncoding.CompressEncoding) &&
        assertTrue(ContentEncoding.toContentEncoding("deflate") == ContentEncoding.DeflateEncoding) &&
        assertTrue(
          ContentEncoding.toContentEncoding("deflate, br, compress") == ContentEncoding.MultipleEncodings(
            Chunk(ContentEncoding.DeflateEncoding, ContentEncoding.BrEncoding, ContentEncoding.CompressEncoding),
          ),
        ) &&
        assertTrue(ContentEncoding.toContentEncoding("garbage") == ContentEncoding.InvalidEncoding)

      },
      test("edge cases") {
        assertTrue(
          ContentEncoding.toContentEncoding(
            ContentEncoding.fromContentEncoding(MultipleEncodings(Chunk())),
          ) == InvalidEncoding,
        ) &&
        assertTrue(
          ContentEncoding.toContentEncoding(
            ContentEncoding.fromContentEncoding(
              MultipleEncodings(
                Chunk(ContentEncoding.DeflateEncoding, ContentEncoding.BrEncoding, ContentEncoding.CompressEncoding),
              ),
            ),
          ) == ContentEncoding.MultipleEncodings(
            Chunk(ContentEncoding.DeflateEncoding, ContentEncoding.BrEncoding, ContentEncoding.CompressEncoding),
          ),
        ) &&
        assertTrue(
          ContentEncoding.toContentEncoding(
            ContentEncoding.fromContentEncoding(
              MultipleEncodings(
                Chunk(ContentEncoding.DeflateEncoding),
              ),
            ),
          ) == ContentEncoding.DeflateEncoding,
        )
      },
    ),
  )
}
