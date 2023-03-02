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
