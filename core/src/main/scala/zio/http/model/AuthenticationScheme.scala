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

import java.io.Serializable

sealed trait AuthenticationScheme extends Product with Serializable
case object Basic                 extends AuthenticationScheme
case object Bearer                extends AuthenticationScheme
case object Digest                extends AuthenticationScheme
case object Hoba                  extends AuthenticationScheme
case object Mutual                extends AuthenticationScheme
case object Negotiate             extends AuthenticationScheme
case object OAuth                 extends AuthenticationScheme
case object ScramSha1             extends AuthenticationScheme
case object ScramSha256           extends AuthenticationScheme
case object Vapid                 extends AuthenticationScheme
