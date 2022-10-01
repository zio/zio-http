package zio.http.model.headers

import zio.Scope
import zio.http.model.headers.HeaderValue.ContentMD5
import zio.http.model.headers.HeaderValue.ContentMD5.{ContentMD5Value, InvalidContentMD5Value, MD5}
import zio.test._
object ContentMD5Spec extends ZIOSpecDefault {
  override def spec: Spec[TestEnvironment with Scope, Any] = suite("ContentMD5 header suite")(
    test("parsing of valid ContentMD5 header") {
      check(Gen.listOfN(32)(Gen.hexChar))(hexChars => {
        val hexString = hexChars.mkString
        val result    = ContentMD5.toContentMD5(hexString)
        assertTrue(result == ContentMD5Value(MD5(hexString)))
      })
    },
    test("parsing of invalid ContentMD5 header") {
      assertTrue(ContentMD5.toContentMD5("") == InvalidContentMD5Value)
      assertTrue(ContentMD5.toContentMD5("TooShort") == InvalidContentMD5Value)
      assertTrue(ContentMD5.toContentMD5("TooLongToBeConsideredValidMD5Hash") == InvalidContentMD5Value)
    },
    test("parsing and encoding is symmetrical") {
      check(Gen.listOfN(32)(Gen.hexChar))(hexChars => {
        val hexString = hexChars.mkString
        assertTrue(hexString == ContentMD5.fromContentMD5(ContentMD5.toContentMD5(hexString)))
      })
    },
  )
}
