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

import Charset._

final case class Charset(value: CharsetType, param: Map[String, String] = Map.empty)

object Charset {

  sealed trait CharsetType
  case object ISO_8859_1 extends CharsetType
  case object UTF_8      extends CharsetType
  case object UTF_16     extends CharsetType
  case object UTF_16BE   extends CharsetType
  case object UTF_16LE   extends CharsetType
  case object US_ASCII   extends CharsetType

}
