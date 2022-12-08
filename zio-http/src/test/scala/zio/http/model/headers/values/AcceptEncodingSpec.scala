package zio.http.model.headers.values

import zio.http.api.internal.HeaderValueCodecs
import zio.http.internal.HttpGen
import zio.http.model.headers.values.AcceptEncoding.MultipleEncodings
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}
import zio.{Chunk, Scope}

object AcceptEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AcceptEncoding suite")(
    suite("Encoding header value transformation should be symmetrical")(
      test("single value parsing") {
        HeaderValueCodecs.acceptEncodingCodec.decode("gzip") match {
          case Right(value) => assertTrue(value == Chunk(("gzip", None)))
          case Left(_)      => assertTrue(false)
        }
      },
      test("single value parsing with q factor") {
        HeaderValueCodecs.acceptEncodingCodec.decode("br;q=0.8") match {
          case Right(value) => assertTrue(value == Chunk(("br", Some(0.8))))
          case Left(_)      => assertTrue(false)
        }
      },
      test("multiple values parsing with q factor") {
        HeaderValueCodecs.acceptEncodingCodec.decode("br;q=0.8,gzip") match {
          case Right(value) => assertTrue(value == Chunk(("br", Some(0.8)), ("gzip", None)))
          case Left(_)      => assertTrue(false)
        }
      },
      test("single value") {
        check(HttpGen.acceptEncodingSingleValueWithWeight) { value =>
          assertTrue(
            AcceptEncoding.toAcceptEncoding(
              AcceptEncoding.fromAcceptEncoding(MultipleEncodings(Chunk(value))),
            ) == MultipleEncodings(Chunk(value)),
          )
        }
      },
      test("multiple values") {
        check(HttpGen.acceptEncoding) { value =>
          assertTrue(AcceptEncoding.toAcceptEncoding(AcceptEncoding.fromAcceptEncoding(value)) == value)
        }
      },
    ),
  )
}
