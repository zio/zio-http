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

package zio.http

import zio.http.Path.Segment

private[zio] trait PathSyntax { module =>
  val Root: Path = Path.root

  val ~~ : Path = Path.empty

  object /: {
    def unapply(path: Path): Option[(String, Path)] =
      for {
        head <- path.segments.headOption.map {
          case Segment.Text(text) => text
          case Segment.Root       => ""
        }
        tail = path.segments.drop(1)
      } yield (head, Path(tail))
  }

  object / {
    def unapply(path: Path): Option[(Path, String)] = {
      if (path.segments.length == 1) {
        val last = path.segments.last match {
          case Segment.Text(text) => text
          case Segment.Root       => ""
        }
        Some(~~ -> last)
      } else if (path.segments.length >= 2) {
        val last = path.segments.last match {
          case Segment.Root       => ""
          case Segment.Text(text) => text
        }
        Some(Path(path.segments.init) -> last)
      } else None
    }
  }
}
