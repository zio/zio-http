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

package zio.http.netty.server

import io.netty.util.AsciiString

import java.text.SimpleDateFormat
import java.util.Date // scalafix:ok;
// import zio.stacktracer.TracingImplicits.disableAutoTrace
private[zio] final class ServerTime(minDuration: Long) {

  private var last: Long               = System.currentTimeMillis()
  private var lastString: CharSequence = ServerTime.format(new Date(last))

  def refresh(): Boolean = {
    val now  = System.currentTimeMillis()
    val diff = now - last
    if (diff > minDuration) {
      last = now
      lastString = ServerTime.format(new Date(last))
      true
    } else {
      false
    }
  }

  def get: CharSequence = lastString

  def refreshAndGet(): CharSequence = {
    refresh()
    get
  }
}

object ServerTime {
  private val format = new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z")

  def format(d: Date): CharSequence = new AsciiString(format.format(d))

  def make(interval: zio.Duration): ServerTime = new ServerTime(interval.toMillis)

  def parse(s: CharSequence): Date = format.parse(s.toString)
}
