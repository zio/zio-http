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
import zio.http.model.headers.values.TransferEncoding.{InvalidEncoding, MultipleEncodings}

object TransferEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("TransferEncoding suite")(
    suite("TransferEncoding header value transformation should be symmetrical")(
      test("gen single value") {
        check(HttpGen.allowTransferEncodingSingleValue) { value =>
          assertTrue(TransferEncoding.toTransferEncoding(TransferEncoding.fromTransferEncoding(value)) == value)
        }
      },
      test("single value") {
        assertTrue(TransferEncoding.toTransferEncoding("chunked") == TransferEncoding.ChunkedEncoding) &&
        assertTrue(TransferEncoding.toTransferEncoding("compress") == TransferEncoding.CompressEncoding) &&
        assertTrue(TransferEncoding.toTransferEncoding("deflate") == TransferEncoding.DeflateEncoding) &&
        assertTrue(
          TransferEncoding.toTransferEncoding("deflate, chunked, compress") == TransferEncoding.MultipleEncodings(
            Chunk(TransferEncoding.DeflateEncoding, TransferEncoding.ChunkedEncoding, TransferEncoding.CompressEncoding),
          ),
        ) &&
        assertTrue(TransferEncoding.toTransferEncoding("garbage") == TransferEncoding.InvalidEncoding)

      },
      test("edge cases") {
        assertTrue(
          TransferEncoding.toTransferEncoding(
            TransferEncoding.fromTransferEncoding(MultipleEncodings(Chunk())),
          ) == InvalidEncoding,
        ) &&
        assertTrue(
          TransferEncoding.toTransferEncoding(
            TransferEncoding.fromTransferEncoding(
              MultipleEncodings(
                Chunk(
                  TransferEncoding.DeflateEncoding,
                  TransferEncoding.ChunkedEncoding,
                  TransferEncoding.CompressEncoding,
                ),
              ),
            ),
          ) == TransferEncoding.MultipleEncodings(
            Chunk(TransferEncoding.DeflateEncoding, TransferEncoding.ChunkedEncoding, TransferEncoding.CompressEncoding),
          ),
        ) &&
        assertTrue(
          TransferEncoding.toTransferEncoding(
            TransferEncoding.fromTransferEncoding(
              MultipleEncodings(
                Chunk(TransferEncoding.DeflateEncoding),
              ),
            ),
          ) == TransferEncoding.DeflateEncoding,
        )
      },
    ),
  )
}
