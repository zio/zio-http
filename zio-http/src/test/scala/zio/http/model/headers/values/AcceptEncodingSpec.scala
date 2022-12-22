package zio.http.model.headers.values

import zio.http.api.HeaderValueCodecs
import zio.http.model.headers.HeaderTypedValues.AcceptEncoding.{BrEncoding, GZipEncoding, MultipleEncodings}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue}
import zio.{Chunk, Scope}

object AcceptEncodingSpec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("AcceptEncoding suite")(
    suite("Encoding header value transformation should be symmetrical")(
      test("single value parsing") {
        HeaderValueCodecs.acceptEncodingCodec.decode("gzip") match {
          case Right(value) => assertTrue(value == MultipleEncodings(Chunk(GZipEncoding(None))))
          case Left(_)      => assertTrue(false)
        }
      },
      test("single value parsing with q factor") {
        HeaderValueCodecs.acceptEncodingCodec.decode("br;q=0.8") match {
          case Right(value) => assertTrue(value == MultipleEncodings(Chunk(BrEncoding(Some(0.8)))))
          case Left(_)      => assertTrue(false)
        }
      },
      test("multiple values parsing with q factor") {
        HeaderValueCodecs.acceptEncodingCodec.decode("br;q=0.8,gzip") match {
          case Right(value) => assertTrue(value == MultipleEncodings(Chunk(BrEncoding(Some(0.8)), GZipEncoding(None))))
          case Left(_)      => assertTrue(false)
        }
      },
      test("multiple values parsing with q factor and spaces") {
        HeaderValueCodecs.acceptEncodingCodec.encode(
          MultipleEncodings(Chunk(BrEncoding(Some(0.8)), GZipEncoding(None))),
        ) match {
          case Right(value) => assertTrue(value == "br;q=0.8,gzip")
          case Left(_)      => assertTrue(false)
        }
      },
    ),
  )
}
