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

package zio.http.netty.client

import zio.test.Assertion._
import zio.test._

import zio.http.internal.HttpGen
import zio.http.netty._
import zio.http.netty.model.Conversions
import zio.http.{Body, Request}

import io.netty.handler.codec.http.HttpHeaderNames

object ClientRequestEncoderSpec extends ZIOSpecDefault with ClientRequestEncoder {

  val anyClientParam: Gen[Sized, Request] = HttpGen.requestGen(
    HttpGen.body(
      Gen.listOf(Gen.alphaNumericString),
    ),
  )

  val clientParamWithAbsoluteUrl = HttpGen.requestGen(
    dataGen = HttpGen.body(
      Gen.listOf(Gen.alphaNumericString),
    ),
    urlGen = HttpGen.genAbsoluteURL,
  )

  def clientParamWithFiniteData(size: Int): Gen[Sized, Request] = HttpGen.requestGen(
    for {
      content <- Gen.alphaNumericStringBounded(size, size)
      data    <- Gen.fromIterable(List(Body.fromString(content)))
    } yield data,
  )

  def spec = suite("EncodeClientParams")(
    test("method") {
      check(anyClientParam) { params =>
        val req = encode(params).map(_.method())
        assertZIO(req)(equalTo(Conversions.methodToNetty(params.method)))
      }
    },
    test("method on Body.RandomAccessFile") {
      check(HttpGen.clientParamsForFileBody()) { params =>
        val req = encode(params).map(_.method())
        assertZIO(req)(equalTo(Conversions.methodToNetty(params.method)))
      }
    },
    suite("uri")(
      test("uri") {
        check(anyClientParam) { params =>
          val req = encode(params).map(_.uri())
          assertZIO(req)(equalTo(params.url.relative.encode))
        }
      },
      test("uri on Body.RandomAccessFile") {
        check(HttpGen.clientParamsForFileBody()) { params =>
          val req = encode(params).map(_.uri())
          assertZIO(req)(equalTo(params.url.relative.encode))
        }
      },
    ),
    test("content-length") {
      check(clientParamWithFiniteData(5)) { params =>
        val req = encode(params).map(
          _.headers().getInt(HttpHeaderNames.CONTENT_LENGTH).toLong,
        )
        assertZIO(req)(equalTo(5L))
      }
    },
    test("host header") {
      check(anyClientParam) { params =>
        val req =
          encode(params).map(i => Option(i.headers().get(HttpHeaderNames.HOST)))
        assertZIO(req)(equalTo(params.url.hostWithOptionalPort))
      }
    },
    test("host header when absolute url") {
      check(clientParamWithAbsoluteUrl) { params =>
        val req = encode(params)
          .map(i => Option(i.headers().get(HttpHeaderNames.HOST)))
        assertZIO(req)(equalTo(params.url.hostWithOptionalPort))
      }
    },
    test("only one host header exists") {
      check(clientParamWithAbsoluteUrl) { params =>
        val req = encode(params)
          .map(_.headers().getAll(HttpHeaderNames.HOST).size)
        assertZIO(req)(equalTo(1))
      }
    },
    test("http version") {
      check(anyClientParam) { params =>
        val req = encode(params).map(i => i.protocolVersion())
        assertZIO(req)(equalTo(Versions.convertToZIOToNetty(params.version)))
      }
    },
  )
}
