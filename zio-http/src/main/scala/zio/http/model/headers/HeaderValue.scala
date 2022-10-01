package zio.http.model.headers

import zio.prelude.Hash

object HeaderValue {
  sealed trait ContentMD5
  object ContentMD5 {
    case class MD5(hexString: String)       extends AnyVal
    case object InvalidContentMD5Value      extends ContentMD5
    case class ContentMD5Value(digest: MD5) extends ContentMD5

    def toContentMD5(hexString: String): ContentMD5 = {
      if (hexString.size == 32) ContentMD5Value(MD5(hexString)) else InvalidContentMD5Value
    }

    def fromContentMD5(contentMD5: ContentMD5): String =
      contentMD5 match {
        case ContentMD5Value(MD5(digest)) => digest
        case InvalidContentMD5Value       => ""
      }
  }
}
