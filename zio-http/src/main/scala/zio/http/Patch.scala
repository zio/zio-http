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

package zio.http

import scala.annotation.tailrec

import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.model.Header.HeaderType
import zio.http.model._

/**
 * Models the set of operations that one would want to apply on a Response.
 */
sealed trait Patch { self =>
  def ++(that: Patch): Patch         = Patch.Combine(self, that)
  def apply(res: Response): Response = {

    @tailrec
    def loop(res: Response, patch: Patch): Response =
      patch match {
        case Patch.Empty                  => res
        case Patch.AddHeaders(headers)    => res.addHeaders(headers)
        case Patch.RemoveHeaders(headers) => res.removeHeaders(headers)
        case Patch.SetStatus(status)      => res.setStatus(status)
        case Patch.Combine(self, other)   => loop(self(res), other)
        case Patch.UpdateHeaders(f)       => res.updateHeaders(f)
      }

    loop(res, self)
  }
}

object Patch {
  case object Empty                                          extends Patch
  final case class AddHeaders(headers: Headers)              extends Patch
  final case class RemoveHeaders(headers: Set[CharSequence]) extends Patch
  final case class SetStatus(status: Status)                 extends Patch
  final case class Combine(left: Patch, right: Patch)        extends Patch
  final case class UpdateHeaders(f: Headers => Headers)      extends Patch

  def empty: Patch = Empty

  def addHeader(headerType: HeaderType)(value: headerType.HeaderValue): Patch =
    addHeader(headerType.name, headerType.render(value))

  def addHeader(header: Header): Patch                          = addHeaders(Headers(header))
  def addHeaders(headers: Headers): Patch                       = AddHeaders(headers)
  def addHeader(name: CharSequence, value: CharSequence): Patch = addHeaders(Headers(name, value))

  def removeHeaders(headerTypes: Set[HeaderType]): Patch = RemoveHeaders(headerTypes.map(_.name))
  def setStatus(status: Status): Patch                   = SetStatus(status)
  def updateHeaders(f: Headers => Headers): Patch        = UpdateHeaders(f)
}
