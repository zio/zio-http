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

import scala.math.BigDecimal.javaBigDecimal2bigDecimal

import zio._
import zio.test._

import zio.schema.{DeriveSchema, Schema}

import zio.http._
import zio.http.codec.{HttpCodec, HttpContentCodec}

object EndpointSpec extends ZIOHttpSpec {
  final case class Data(name: String, age: Int)
  object Data {
    implicit val schema: Schema[Data] = DeriveSchema.gen[Data]
  }
  def spec = suite("EndpointSpec")(
    test("Derive correct HttpContentCodec for endpoint") {
      val endpoint = Endpoint(Method.GET / "image" / "metadata")
        .in[Data]
        .out[Data]

      val inCodec  =
        endpoint.input.asInstanceOf[HttpCodec.Combine[_, _, _, _, _]].right.asInstanceOf[HttpCodec.Content[Data]].codec
      val outCodec =
        endpoint.output.asInstanceOf[HttpCodec.Combine[_, _, _, _, _]].left.asInstanceOf[HttpCodec.Content[Data]].codec

      val expectedCodec = HttpContentCodec.fromSchema[Data]

      assertTrue(inCodec == expectedCodec, outCodec == expectedCodec)
    },
  )

  def testEndpoint[R](service: Routes[R, Nothing])(
    url: String,
    expected: String,
  ): ZIO[Scope & R, Response, TestResult] =
    testEndpointWithHeaders(service)(url, headers = List.empty, expected)

  def testEndpointWithHeaders[R](service: Routes[R, Nothing])(
    url: String,
    headers: List[(String, String)],
    expected: String,
  ): ZIO[Scope & R, Response, TestResult] = {
    val request = Request
      .get(url = URL.decode(url).toOption.get)
      .addHeaders(headers.foldLeft(Headers.empty) { case (hs, (k, v)) => hs ++ Headers(k, v) })
    for {
      response <- service.runZIO(request)
      body     <- response.body.asString.orDie
    } yield assertTrue(body == "\"" + expected + "\"") // TODO: Real JSON Encoding
  }

  final case class ImageMetadata(description: String, createdAt: Instant)
  object ImageMetadata {
    implicit val schema: Schema[ImageMetadata] = DeriveSchema.gen[ImageMetadata]
  }

  def extractStatus(response: Response): Status = response.status
}
