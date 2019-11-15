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

final case class RequestHeader(
  accept: Option[Accept] = None,
  acceptCharset: Option[AcceptCharset] = None,
  acceptEncoding: Option[AcceptEncoding] = None,
  acceptLanguage: Option[AcceptLanguage] = None,
  authorization: Option[Authorization] = None,
  expect: Option[Expect] = None,
  from: Option[From] = None,
  host: Option[Host] = None,
  ifMatch: Option[IfMatch] = None,
  ifModifiedSince: Option[IfModifiedSince] = None,
  ifNoneMatch: Option[IfNoneMatch] = None,
  ifRange: Option[IfRange] = None,
  ifUnmodifiedSince: Option[IfUnmodifiedSince] = None,
  maxForwards: Option[MaxForwards] = None,
  proxyAuthorization: Option[ProxyAuthorization] = None,
  range: Option[Range] = None,
  referer: Option[Referer] = None,
  te: Option[TransferEncoding] = None,
  userAgent: Option[UserAgent] = None
)
