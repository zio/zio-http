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

package zio.http.model.headers.values

sealed trait ContentRange {
  def start: Option[Int]
  def end: Option[Int]
  def total: Option[Int]
  def unit: String
}

object ContentRange {
  final case class EndTotal(unit: String, s: Int, e: Int, t: Int) extends ContentRange {
    def start: Option[Int] = Some(s)
    def end: Option[Int]   = Some(e)
    def total: Option[Int] = Some(t)
  }
  final case class StartEnd(unit: String, s: Int, e: Int)         extends ContentRange {
    def start: Option[Int] = Some(s)
    def end: Option[Int]   = Some(e)
    def total: Option[Int] = None
  }
  final case class RangeTotal(unit: String, t: Int)               extends ContentRange {
    def start: Option[Int] = None
    def end: Option[Int]   = None
    def total: Option[Int] = Some(t)
  }

  private val contentRangeStartEndTotalRegex = """(\w+) (\d+)-(\d+)/(\d+)""".r
  private val contentRangeStartEndRegex      = """(\w+) (\d+)-(\d+)/*""".r
  private val contentRangeTotalRegex         = """(\w+) */(\d+)""".r

  def parse(s: CharSequence): Either[String, ContentRange] =
    s match {
      case contentRangeStartEndTotalRegex(unit, start, end, total) =>
        Right(EndTotal(unit, start.toInt, end.toInt, total.toInt))
      case contentRangeStartEndRegex(unit, start, end)             =>
        Right(StartEnd(unit, start.toInt, end.toInt))
      case contentRangeTotalRegex(unit, total)                     =>
        Right(RangeTotal(unit, total.toInt))
      case _                                                       =>
        Left("Invalid content range")
    }

  def render(c: ContentRange): String =
    c match {
      case EndTotal(unit, start, end, total) =>
        s"$unit $start-$end/$total"
      case StartEnd(unit, start, end)        =>
        s"$unit $start-$end/*"
      case RangeTotal(unit, total)           =>
        s"$unit */$total"
    }

}
