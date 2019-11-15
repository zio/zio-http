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
    If-None-Match request-header field is used with a method to make
    it conditional. A client that has one or more entities previously
    obtained from the resource can verify that none of those entities is
    current by including a list of their associated entity tags in the
    If-None-Match header field. The purpose of this feature is to allow
    efficient updates of cached information with a minimum amount of
    transaction overhead. It is also used to prevent a method (e.g. PUT)
    from inadvertently modifying an existing resource when the client
    believes that the resource does not exist.
 */
final case class IfNoneMatch(etags: List[ETag])
