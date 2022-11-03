package zio.http.model.headers.values

sealed trait ContentDisposition

object ContentDisposition {
  final case class Attachment(filename: Option[String])             extends ContentDisposition
  final case class Inline(filename: Option[String])                 extends ContentDisposition
  final case class FormData(name: String, filename: Option[String]) extends ContentDisposition
  case object Invalid                                               extends ContentDisposition

  private val AttachmentRegex         = """attachment; filename="(.*)"""".r
  private val InlineRegex             = """inline; filename="(.*)"""".r
  private val FormDataRegex           = """form-data; name="(.*)"; filename="(.*)"""".r
  private val FormDataNoFileNameRegex = """form-data; name="(.*)"""".r

  def toContentDisposition(contentDisposition: CharSequence): ContentDisposition = {
    val asString = contentDisposition.toString
    if (asString.startsWith("attachment")) {
      contentDisposition match {
        case AttachmentRegex(filename) => Attachment(Some(filename))
        case _                         => Attachment(None)
      }
    } else if (asString.startsWith("inline")) {
      contentDisposition match {
        case InlineRegex(filename) => Inline(Some(filename))
        case _                     => Inline(None)
      }
    } else if (asString.startsWith("form-data")) {
      contentDisposition match {
        case FormDataRegex(name, filename) => FormData(name, Some(filename))
        case FormDataNoFileNameRegex(name) => FormData(name, None)
        case _                             => Invalid
      }
    } else {
      Invalid
    }
  }

  def fromContentDisposition(contentDisposition: ContentDisposition): String = {
    contentDisposition match {
      case Attachment(filename)     => s"attachment; ${filename.map("filename=" + _).getOrElse("")}"
      case Inline(filename)         => s"inline; ${filename.map("filename=" + _).getOrElse("")}"
      case FormData(name, filename) => s"form-data; name=$name; ${filename.map("filename=" + _).getOrElse("")}"
      case Invalid                  => ""
    }
  }

  val inline: ContentDisposition                                   = Inline(None)
  val attachment: ContentDisposition                               = Attachment(None)
  def inline(filename: String): ContentDisposition                 = Inline(Some(filename))
  def attachment(filename: String): ContentDisposition             = Attachment(Some(filename))
  def formData(name: String): ContentDisposition                   = FormData(name, None)
  def formData(name: String, filename: String): ContentDisposition = FormData(name, Some(filename))
}
