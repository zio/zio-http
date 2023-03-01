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

package zio.http.service.logging

import zio.http.logging.LogLevel
import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object LogLevelTransform {
  implicit class LogLevelWrapper(level: LogLevel) {
    def toNettyLogLevel: io.netty.handler.logging.LogLevel = level match {
      case zio.http.logging.LogLevel.Trace => io.netty.handler.logging.LogLevel.TRACE
      case zio.http.logging.LogLevel.Debug => io.netty.handler.logging.LogLevel.DEBUG
      case zio.http.logging.LogLevel.Info  => io.netty.handler.logging.LogLevel.INFO
      case zio.http.logging.LogLevel.Warn  => io.netty.handler.logging.LogLevel.WARN
      case zio.http.logging.LogLevel.Error => io.netty.handler.logging.LogLevel.ERROR
    }
  }
}
