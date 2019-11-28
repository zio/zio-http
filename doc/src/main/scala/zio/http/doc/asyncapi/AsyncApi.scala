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

package zio.http.doc.asyncapi

import java.net.URI

import zio.http.doc.asyncapi.model._

/**
 * Root document object for the API specification
 *
 * @param asyncapi Async API Specification version
 * @param id  Identifier for application
 * @param info API metadata
 * @param servers Server connection details
 * @param channels Available channels and messages for API
 * @param components Various schemas for the spec
 * @param tags Additonal metadata
 * @param externalDocs External documentation
 */
final case class AsyncApi[T](
  asyncapi: Version,
  id: Option[URI],
  info: Info,
  servers: Option[Map[String, Server]],
  channels: Map[String, Channel[T]],
  components: Option[List[Component[T]]],
  tags: Option[List[Tag]],
  externalDocs: Option[ExternalDocumentation]
)
