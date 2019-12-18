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
 *  See the License for the specific Language governing permissions and
 *  limitations under the License.
 *
 */

package zio.http.model

import zio.http.model.ContentCoding._

final case class ContentCoding(value: ContentCodingType, q: Option[QValue] = None)

object ContentCoding {

  sealed trait ContentCodingType

  final object ContentCodingType {
    final case object COMPRESS extends ContentCodingType
    final case object DEFLATE  extends ContentCodingType
    final case object GZIP     extends ContentCodingType
  }

}
