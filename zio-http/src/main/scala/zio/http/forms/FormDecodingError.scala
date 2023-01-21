package zio.http.forms

/**
 * Represents a form decoding error.
 */
sealed trait FormDecodingError { self =>
  import FormDecodingError._

  def message: String = self match {
    case ContentDispositionMissingName     => "Content-Disposition header is missing 'name' field"
    case FormDataMissingContentDisposition => "Form data is missing Content-Disposition header"
    case InvalidCharset(msg)               => s"Invalid charset: $msg"
    case InvalidURLEncodedFormat(msg)      => s"Invalid URL encoded format: $msg"
    case BoundaryNotFoundInContent         => "Boundary not found in content or was malformed."
  }

  def asException: FormDecodingException = FormDecodingException(self)
}

object FormDecodingError {

  final case class FormDecodingException(error: FormDecodingError) extends Exception

  case object ContentDispositionMissingName extends FormDecodingError

  case object FormDataMissingContentDisposition extends FormDecodingError

  final case class InvalidCharset(msg: String) extends FormDecodingError

  final case class InvalidURLEncodedFormat(msg: String) extends FormDecodingError

  case object BoundaryNotFoundInContent extends FormDecodingError
}
