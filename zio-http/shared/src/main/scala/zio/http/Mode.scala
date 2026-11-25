package zio.http

/**
 * The mode the application runs in: `Mode.Dev`, `Mode.Preprod` or `Mode.Prod`.
 * `Mode.current` reads it from the environment.
 */
sealed trait Mode extends Product with Serializable { self =>

  /** Returns `true` when this mode is `Mode.current`. */
  def isActive: Boolean = Mode.current == self
}

/**
 * The modes and the way the current mode is read.
 */
object Mode {

  /**
   * Returns the mode named by the `zio.http.mode` system property. When the
   * property is not set, it uses the `ZIO_HTTP_MODE` environment variable, and
   * when neither is set the result is `Mode.Prod`. The name is matched ignoring
   * case. An unknown name, including the empty string, prints a warning to
   * standard error and gives `Mode.Prod`. The value is read on every call.
   */
  def current: Mode = {
    val prop = java.lang.System.getProperty("zio.http.mode")
    val env  = java.lang.System.getenv("ZIO_HTTP_MODE")
    if (prop != null) fromString(prop)
    else if (env != null) fromString(env)
    else Prod
  }

  private def fromString(str: String): Mode =
    if (str.equalsIgnoreCase("dev")) Dev
    else if (str.equalsIgnoreCase("preprod")) Preprod
    else if (str.equalsIgnoreCase("prod")) Prod
    else {
      Console.err.println(
        s"[WARN] Unknown mode '$str', supported modes are 'dev', 'preprod' and 'prod'. Falling back to 'prod'.",
      )
      Prod
    }

  /** Returns `true` when `Mode.current` is `Mode.Dev`. */
  def isDev: Boolean = current == Dev

  /** Returns `true` when `Mode.current` is `Mode.Preprod`. */
  def isPreprod: Boolean = current == Preprod

  /** Returns `true` when `Mode.current` is `Mode.Prod`. */
  def isProd: Boolean = current == Prod

  /** The development mode, selected by the name `dev`. */
  case object Dev extends Mode

  /** The pre-production mode, selected by the name `preprod`. */
  case object Preprod extends Mode

  /**
   * The production mode, selected by the name `prod`. It is the mode when
   * nothing is configured.
   */
  case object Prod extends Mode

}
