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
import zio.http.{Body, QueryParams, Request, URL, ZIOHttpSpec}

import io.netty.handler.codec.http.HttpHeaderNames

object NettyRequestEncoderSpec extends ZIOHttpSpec {
  import NettyRequestEncoder._

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

  val clientParamWithEmptyPathAndQueryParams = HttpGen.requestGen(
    dataGen = HttpGen.body(
      Gen.listOf(Gen.alphaNumericString),
    ),
    urlGen = Gen.const(URL.empty.addQueryParams(QueryParams(("p", "1")))),
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
          assertZIO(req)(equalTo(params.url.relative.addLeadingSlash.encode))
        }
      },
      test("uri on Body.RandomAccessFile") {
        check(HttpGen.clientParamsForFileBody()) { params =>
          val req = encode(params).map(_.uri())
          assertZIO(req)(equalTo(params.url.relative.addLeadingSlash.encode))
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
        assertZIO(req)(equalTo(params.url.hostPort))
      }
    },
    test("host header when absolute url") {
      check(clientParamWithAbsoluteUrl) { params =>
        val req = encode(params)
          .map(i => Option(i.headers().get(HttpHeaderNames.HOST)))
        assertZIO(req)(equalTo(params.url.hostPort))
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
        assertZIO(req)(equalTo(Conversions.versionToNetty(params.version)))
      }
    },
    test("url with an empty path and query params") {
      check(clientParamWithEmptyPathAndQueryParams) { params =>
        val uri = encode(params).map(_.uri)
        assertZIO(uri)(not(equalTo(params.url.encode))) &&
        assertZIO(uri)(equalTo(params.url.addLeadingSlash.encode))
      }
    },
    test("leading slash added to path") {
      val url     = URL.decode("https://api.github.com").toOption.get / "something" / "else"
      val req     = Request(url = url)
      val encoded = encode(req).map(_.uri)
      assertZIO(encoded)(equalTo("/something/else"))
    },
  )
}
