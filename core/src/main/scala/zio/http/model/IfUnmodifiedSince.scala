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
import java.time.LocalDateTime

/*
   If-Unmodified-Since request-header field used to indicate the
   requested resource has not been modified since the time specified
   in this field, the server SHOULD perform the requested operation
   as if the If-Unmodified-Since header were not present.
 */
final case class IfUnmodifiedSince(value: LocalDateTime)
