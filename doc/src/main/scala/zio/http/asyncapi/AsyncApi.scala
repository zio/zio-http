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

package zio.http.asyncapi

import java.net.URI
import java.nio.channels.Channels

import zio.http.asyncapi.model._

final case class AsyncApi(
  asyncapi: AsyncVersion,
  id: Option[URI],
  info: Info,
  servers: List[Server],
  channels: Channels,
  components: List[Component],
  tags: List[Tag],
  externalDocs: ExternalDocumentation
)
