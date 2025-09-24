package zio.http

sealed trait Mode extends Product with Serializable { self =>
  def isActive: Boolean = Mode.current == self
}

object Mode {

  def current: Mode = {
    val prop = java.lang.System.getProperty("zio.http.mode")
    val env  = java.lang.System.getenv("ZIO_HTTP_MODE")
    if (prop != null) fromString(prop)
    else if (env != null) fromString(env)
    else Dev
  }

  private def fromString(str: String): Mode =
    if (str.equalsIgnoreCase("dev")) Dev
    else if (str.equalsIgnoreCase("preprod")) Preprod
    else if (str.equalsIgnoreCase("prod")) Prod
    else {
      Console.err.println(
        s"[WARN] Unknown mode '$str', supported modes are 'dev', 'preprod' and 'prod'. Falling back to 'dev'.",
      )
      Dev
    }

  def isDev: Boolean     = current == Dev
  def isPreprod: Boolean = current == Preprod
  def isProd: Boolean    = current == Prod

  case object Dev     extends Mode
  case object Preprod extends Mode
  case object Prod    extends Mode

}
