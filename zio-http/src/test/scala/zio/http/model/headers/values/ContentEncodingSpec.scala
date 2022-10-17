package zio.http.model.headers.values

import zio.{Chunk, Scope}
import zio.http.internal.HttpGen
import zio.http.model.headers.values.ContentEncoding.{InvalidEncoding, MultipleEncodings}
import zio.test.{Spec, TestEnvironment, ZIOSpecDefault, assertTrue, check}

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
