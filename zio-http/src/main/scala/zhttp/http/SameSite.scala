package zhttp.http

sealed trait SameSite
object SameSite {
  case object Lax    extends SameSite { override def toString = "Lax"    }
  case object Strict extends SameSite { override def toString = "Strict" }
  case object None   extends SameSite { override def toString = "None"   }
}
