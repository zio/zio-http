package zio.http.service

import zio.logging.{LogFormat, Logger}
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

/**
 * Base trait to configure logging. Feel free to edit this file as per your
 * requirements to slice and dice internal logging.
 */
trait Logging {

  /**
   * Controls if you want to pipe netty logs into the zio-http logger.
   */
  val EnableNettyLogging: Boolean = false

  /**
   * Name of the property that is used to read the log level from system
   * properties.
   */
  private val PropName = "ZIOHttpLogLevel"

  /**
   * Global Logging instance used to add log statements everywhere in the
   * application.
   */
  private[zio] val Log: Logger =
    Logger.console.detectLevelFromProps(PropName).withFormat(LogFormat.inlineColored)

}
