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

package zio.http.internal

/** Operators that check query parameters. */
trait QueryChecks[+A] { self: QueryOps[A] with A =>

  /**
   * Returns `true` when an entry with this key exists, including one without
   * values. On a `URL` or a `Request` the name is compared with the `String`
   * key by `equals`, so a non-`String` `CharSequence` never matches;
   * `QueryParams` overrides it to compare `name.toString`.
   */
  def hasQueryParam(name: CharSequence): Boolean =
    queryParameters.seq.exists(_.getKey == name)

  /**
   * Returns the number of entries in `queryParameters` with this key, so
   * `a=1&a=2` gives 1 on a URL; `QueryParams` overrides it to count values.
   */
  def valueCount(name: CharSequence): Int =
    queryParameters.seq.count(_.getKey == name)
}
