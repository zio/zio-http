package zio.http.internal

sealed trait CaseMode

object CaseMode {
  case object Sensitive   extends CaseMode
  case object Insensitive extends CaseMode
}
