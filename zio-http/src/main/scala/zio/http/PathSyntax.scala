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

private[zio] trait PathSyntax { module =>
  val Root: Path = Path.root

  val Empty: Path = Path.empty

  object /: {
    def unapply(path: Path): Option[(String, Path)] =
      if (path.leadingSlash) Some(("", path.dropLeadingSlash))
      else if (path.segments.nonEmpty) Some((path.segments.head, path.copy(segments = path.segments.drop(1))))
      else None
  }

  object / {
    def unapply(path: Path): Option[(Path, String)] = {
      if (path.trailingSlash) Some((path.dropTrailingSlash, ""))
      else if (path.segments.nonEmpty) Some((path.copy(segments = path.segments.dropRight(1)), path.segments.last))
      else None
    }
  }
}
