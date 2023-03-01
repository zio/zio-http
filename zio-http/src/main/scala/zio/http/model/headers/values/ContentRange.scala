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
  final case class ContentRangeStartEndTotal(unit: String, s: Int, e: Int, t: Int) extends ContentRange {
    def start: Option[Int] = Some(s)
    def end: Option[Int]   = Some(e)
    def total: Option[Int] = Some(t)
  }
  final case class ContentRangeStartEnd(unit: String, s: Int, e: Int)              extends ContentRange {
    def start: Option[Int] = Some(s)
    def end: Option[Int]   = Some(e)
    def total: Option[Int] = None
  }
  final case class ContentRangeTotal(unit: String, t: Int)                         extends ContentRange {
    def start: Option[Int] = None
    def end: Option[Int]   = None
    def total: Option[Int] = Some(t)
  }
  case object InvalidContentRange                                                  extends ContentRange {
    def start: Option[Int] = None
    def end: Option[Int]   = None
    def total: Option[Int] = None
    def unit: String       = ""
  }

  val contentRangeStartEndTotalRegex = """(\w+) (\d+)-(\d+)/(\d+)""".r
  val contentRangeStartEndRegex      = """(\w+) (\d+)-(\d+)/*""".r
  val contentRangeTotalRegex         = """(\w+) */(\d+)""".r

  def toContentRange(s: CharSequence): ContentRange =
    s match {
      case contentRangeStartEndTotalRegex(unit, start, end, total) =>
        ContentRangeStartEndTotal(unit, start.toInt, end.toInt, total.toInt)
      case contentRangeStartEndRegex(unit, start, end)             =>
        ContentRangeStartEnd(unit, start.toInt, end.toInt)
      case contentRangeTotalRegex(unit, total)                     =>
        ContentRangeTotal(unit, total.toInt)
      case _                                                       => InvalidContentRange
    }

  def fromContentRange(c: ContentRange): String =
    c match {
      case ContentRangeStartEndTotal(unit, start, end, total) =>
        s"$unit $start-$end/$total"
      case ContentRangeStartEnd(unit, start, end)             =>
        s"$unit $start-$end/*"
      case ContentRangeTotal(unit, total)                     =>
        s"$unit */$total"
      case InvalidContentRange                                =>
        ""
    }

}
