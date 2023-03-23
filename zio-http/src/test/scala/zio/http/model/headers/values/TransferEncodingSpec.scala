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
import zio.{NonEmptyChunk, Scope}

import zio.http.internal.HttpGen
import zio.http.model.Header.TransferEncoding
import zio.http.model.Header.TransferEncoding.Multiple

object TransferEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TransferEncoding suite")(
    suite("TransferEncoding header value transformation should be symmetrical")(
      test("gen single value") {
        check(HttpGen.allowTransferEncodingSingleValue) { value =>
          assertTrue(TransferEncoding.parse(TransferEncoding.render(value)) == Right(value))
        }
      },
      test("single value") {
        assertTrue(
          TransferEncoding.parse("chunked") == Right(TransferEncoding.Chunked),
          TransferEncoding.parse("compress") == Right(TransferEncoding.Compress),
          TransferEncoding.parse("deflate") == Right(TransferEncoding.Deflate),
          TransferEncoding.parse("deflate, chunked, compress") == Right(
            TransferEncoding.Multiple(
              NonEmptyChunk(TransferEncoding.Deflate, TransferEncoding.Chunked, TransferEncoding.Compress),
            ),
          ),
          TransferEncoding.parse("garbage").isLeft,
        )

      },
      test("edge cases") {
        assertTrue(
          TransferEncoding
            .parse(" ")
            .isLeft,
          TransferEncoding.parse(
            TransferEncoding.render(
              Multiple(
                NonEmptyChunk(
                  TransferEncoding.Deflate,
                  TransferEncoding.Chunked,
                  TransferEncoding.Compress,
                ),
              ),
            ),
          ) == Right(
            TransferEncoding.Multiple(
              NonEmptyChunk(TransferEncoding.Deflate, TransferEncoding.Chunked, TransferEncoding.Compress),
            ),
          ),
          TransferEncoding.parse(
            TransferEncoding.render(
              Multiple(
                NonEmptyChunk(TransferEncoding.Deflate),
              ),
            ),
          ) == Right(TransferEncoding.Deflate),
        )
      },
    ),
  )
}
