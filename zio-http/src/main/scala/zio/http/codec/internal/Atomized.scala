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

package zio.http.codec.internal

import zio.http.codec.HttpCodec

private[http] final case class Atomized[A](
  method: A,
  status: A,
  path: A,
  query: A,
  header: A,
  content: A,
) {
  def get(tag: HttpCodec.AtomTag): A = {
    tag match {
      case HttpCodec.AtomTag.Status  => status
      case HttpCodec.AtomTag.Path    => path
      case HttpCodec.AtomTag.Content => content
      case HttpCodec.AtomTag.Query   => query
      case HttpCodec.AtomTag.Header  => header
      case HttpCodec.AtomTag.Method  => method
    }
  }

  def update(tag: HttpCodec.AtomTag)(f: A => A): Atomized[A] = {
    tag match {
      case HttpCodec.AtomTag.Status  => copy(status = f(status))
      case HttpCodec.AtomTag.Path    => copy(path = f(path))
      case HttpCodec.AtomTag.Content => copy(content = f(content))
      case HttpCodec.AtomTag.Query   => copy(query = f(query))
      case HttpCodec.AtomTag.Header  => copy(header = f(header))
      case HttpCodec.AtomTag.Method  => copy(method = f(method))
    }
  }
}
private[http] object Atomized {
  def apply[A](defValue: => A): Atomized[A] = Atomized(defValue, defValue, defValue, defValue, defValue, defValue)
}
