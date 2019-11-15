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

/*

    Transfer-coding is a request header property used to indicate
    an encoding transformation that has been, can be, or may need
    to be applied to an entity-body in order to ensure
    "safe transport" through the network. Not be conflated with
    content-type encoding. This encoding is particular to the message,
    being transported not the entity itself.

 */
import TransferEncoding._

sealed abstract class TransferEncoding(transferEncodings: List[TransferEncodingType]) {
  override def toString(): String = s"Tranfer-Encoding: ${transferEncodings.mkString(",")}"
}

object TransferEncoding {
  sealed trait TransferEncodingType
  final case object CHUNKED  extends TransferEncodingType
  final case object IDENTITY extends TransferEncodingType
  final case object GZIP     extends TransferEncodingType
  final case object COMPRESS extends TransferEncodingType
  final case object DEFLATE  extends TransferEncodingType
}
