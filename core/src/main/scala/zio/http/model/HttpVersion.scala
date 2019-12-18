/*
 *
 *  Copyright 2017-2019 John A. De Goes and the ZIO Contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package zio.http.model

sealed abstract class HttpVersion(major: Int, minor: Int) {
  override def toString: String = s"HTTP / $major.$minor"
}

final object HttpVersion {
  final case object HTTP_0_9 extends HttpVersion(0, 9)
  final case object HTTP_1_0 extends HttpVersion(1, 0)
  final case object HTTP_1_1 extends HttpVersion(1, 1)
  final case object HTTP_2_0 extends HttpVersion(2, 0)
  final case object HTTP_3_0 extends HttpVersion(3, 0)
}
