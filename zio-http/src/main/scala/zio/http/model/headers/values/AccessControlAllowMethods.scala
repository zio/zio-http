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

package zio.http.model.headers.values

import zio.Chunk

import zio.http.model.Method

sealed trait AccessControlAllowMethods

object AccessControlAllowMethods {

  final case class AllowMethods(methods: Chunk[Method]) extends AccessControlAllowMethods

  case object AllowAllMethods extends AccessControlAllowMethods

  case object NoMethodsAllowed extends AccessControlAllowMethods

  def fromAccessControlAllowMethods(accessControlAllowMethods: AccessControlAllowMethods): String =
    accessControlAllowMethods match {
      case AllowMethods(methods) => methods.map(_.toString()).mkString(", ")
      case AllowAllMethods       => "*"
      case NoMethodsAllowed      => ""
    }

  def toAccessControlAllowMethods(value: String): AccessControlAllowMethods = {
    value match {
      case ""          => NoMethodsAllowed
      case "*"         => AllowAllMethods
      case methodNames =>
        AllowMethods(
          Chunk.fromArray(
            methodNames
              .split(",")
              .map(_.trim)
              .map(Method.fromString),
          ),
        )
    }
  }
}
