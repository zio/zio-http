/*
 * Copyright 2017-2019 John A. De Goes and the ZIO Contributors
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

sealed abstract class Method(val name: String) {
  def toString: String = name
}

object Method {
  final case object GET  extends Method("GET")
  final case object HEAD extends Method("HEAD")
  final case object POST extends Method("POST")
  final case object PUT extends Method("PUT")
  final case object DELETE extends Method("DELETE")
  final case object CONNECT extends Method("CONNECT")
  final case object OPTIONS extends Method("OPTIONS")
  final case object TRACE extends Method("TRACE")
  final case object PATCH extends Method("PATCH")
}
