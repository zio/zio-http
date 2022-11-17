package zio.http.model.headers.values

/** Pragma header value. */
sealed trait Pragma

object Pragma{
  /** Pragma no-cache value.*/
  case object PragmaNoCacheValue extends Pragma
  /** Invalid pragma value.*/
  case object InvalidPragmaValue extends Pragma

  def toPragma(value: String): Pragma =
    value.toLowerCase match {
      case "no-cache" => PragmaNoCacheValue
      case _ => InvalidPragmaValue
    }

  def fromPragma(pragma: Pragma): String =
    pragma match {
      case PragmaNoCacheValue => "no-cache"
      case InvalidPragmaValue => ""
    }
}


