/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
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

package zio.http.endpoint.openapi

import java.net.URI

import zio.http.codec.Doc

private[openapi] abstract class InfoCompatibility
    extends scala.runtime.AbstractFunction6[
      String,
      Option[Doc],
      Option[URI],
      Option[OpenAPI.Contact],
      Option[OpenAPI.License],
      String,
      OpenAPI.Info,
    ]

private[openapi] abstract class OperationCompatibility
    extends scala.runtime.AbstractFunction12[
      List[String],
      Option[String],
      Option[Doc],
      Option[OpenAPI.ExternalDoc],
      Option[String],
      Set[OpenAPI.ReferenceOr[OpenAPI.Parameter]],
      Option[OpenAPI.ReferenceOr[OpenAPI.RequestBody]],
      Map[OpenAPI.StatusOrDefault, OpenAPI.ReferenceOr[OpenAPI.Response]],
      Map[String, OpenAPI.ReferenceOr[OpenAPI.Callback]],
      Option[Boolean],
      List[OpenAPI.SecurityScheme.SecurityRequirement],
      List[OpenAPI.Server],
      OpenAPI.Operation,
    ]

private[openapi] abstract class TagCompatibility
    extends scala.runtime.AbstractFunction3[
      String,
      Option[Doc],
      Option[OpenAPI.ExternalDoc],
      OpenAPI.Tag,
    ]
