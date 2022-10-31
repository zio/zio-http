package zio.http.model.headers.values

import zio.http.internal.HttpGen
import zio.http.model.headers.values.TransferEncoding.{InvalidEncoding, MultipleEncodings}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}
import zio.{Chunk, Scope}

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
