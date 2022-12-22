//package zio.http.model.headers.values
//
//sealed trait ContentMd5
//
//object ContentMd5 {
//  final case class ContentMd5Value(value: String) extends ContentMd5
//  object InvalidContentMd5Value                   extends ContentMd5
//
//  val MD5Regex = """[A-Fa-f0-9]{32}""".r
//
//  def toContentMd5(value: CharSequence): ContentMd5 =
//    value match {
//      case MD5Regex() => ContentMd5Value(value.toString)
//      case _          => InvalidContentMd5Value
//    }
//
//  def fromContentMd5(contentMd5: ContentMd5): String =
//    contentMd5 match {
//      case ContentMd5Value(value) => value
//      case _                      => ""
//    }
//}
