/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.service

import zio.http.logging.{LogFormat, Logger}
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
