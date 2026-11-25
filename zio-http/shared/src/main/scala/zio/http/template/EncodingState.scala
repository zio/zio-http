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

package zio.http.template

private[template] sealed trait EncodingState {

  /**
   * The text written between two child nodes at this level: empty, or a line
   * break followed by the indentation.
   */
  def nextElemSeparator: String

  /** Returns the state for the children of an element at this level. */
  def inner: EncodingState
}

/** The two ways of laying out rendered HTML: on one line, or indented. */
object EncodingState {

  /**
   * The layout that puts rendered HTML on one line, with nothing between nodes.
   */
  case object NoIndentation extends EncodingState {

    /** The empty string. */
    val nextElemSeparator: String = ""

    /** Returns `NoIndentation`. */
    def inner: EncodingState = NoIndentation
  }

  /**
   * The layout that puts line breaks between nodes, with `current * spaces`
   * spaces of indentation at nesting level `current`.
   */
  final case class Indentation(current: Int, spaces: Int) extends EncodingState {

    /** A line break followed by `current * spaces` spaces. */
    val nextElemSeparator: String = "\n" + (" " * (current * spaces))

    /**
     * Returns the state one level deeper: `Indentation(current + 1, spaces)`.
     */
    def inner: EncodingState = Indentation(current + 1, spaces)
  }
}
