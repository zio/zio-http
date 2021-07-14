package zhttp.http

sealed trait SameSite
object SameSite {
  case object Strict extends SameSite
  case object Lax    extends SameSite
  case object None   extends SameSite

  def site(sameSite: Option[SameSite]): Option[String] = sameSite match {
    case Some(site) =>
      site match {
        case SameSite.Strict => Some("Strict")
        case SameSite.Lax    => Some("Lax")
        case SameSite.None   => Some("None")
      }
    case scala.None => Some("Lax")
  }
}
