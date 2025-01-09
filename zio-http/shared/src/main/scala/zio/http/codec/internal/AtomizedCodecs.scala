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

import zio.Chunk
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.codec.HttpCodec._
import zio.http.codec._

private[http] final case class AtomizedCodecs(
  method: Chunk[SimpleCodec[zio.http.Method, _]],
  path: Chunk[PathCodec[_]],
  query: Chunk[Query[_, _]],
  header: Chunk[Header[_]],
  headerCustom: Chunk[HeaderCustom[_]],
  content: Chunk[BodyCodec[_]],
  status: Chunk[SimpleCodec[zio.http.Status, _]],
) { self =>
  def append(atom: Atom[_, _]): AtomizedCodecs = atom match {
    case path0: Path[_]            => self.copy(path = path :+ path0.pathCodec)
    case method0: Method[_]        => self.copy(method = method :+ method0.codec)
    case query0: Query[_, _]       => self.copy(query = query :+ query0)
    case header0: Header[_]        => self.copy(header = header :+ header0)
    case header0: HeaderCustom[_]  => self.copy(headerCustom = headerCustom :+ header0)
    case status0: Status[_]        => self.copy(status = status :+ status0.codec)
    case content0: Content[_]      =>
      self.copy(content = content :+ BodyCodec.Single(content0.codec, content0.name))
    case stream0: ContentStream[_] =>
      self.copy(content = content :+ BodyCodec.Multiple(stream0.codec, stream0.name))
  }

  def makeInputsBuilder(): Mechanic.InputsBuilder = {
    Atomized(
      method = Array.ofDim(method.length),
      path = Array.ofDim(path.length),
      query = Array.ofDim(query.length),
      header = Array.ofDim(header.length),
      headerCustom = Array.ofDim(headerCustom.length),
      content = Array.ofDim(content.length),
      status = Array.ofDim(status.length),
    )
  }

  def optimize: AtomizedCodecs =
    AtomizedCodecs(
      method = method.materialize,
      path = path.materialize,
      query = query.materialize,
      header = header.materialize,
      headerCustom = headerCustom.materialize,
      content = content.materialize,
      status = status.materialize,
    )
}

private[http] object AtomizedCodecs {
  val empty: AtomizedCodecs =
    AtomizedCodecs(
      method = Chunk.empty,
      path = Chunk.empty,
      query = Chunk.empty,
      header = Chunk.empty,
      headerCustom = Chunk.empty,
      content = Chunk.empty,
      status = Chunk.empty,
    )

  def flatten[R, A](in: HttpCodec[R, A]): AtomizedCodecs = {
    val atoms = flattenedAtoms(in)

    atoms
      .foldLeft(AtomizedCodecs.empty) { case (acc, atom) =>
        acc.append(atom)
      }
      .optimize
  }

  private def flattenedAtoms[R, A](in: HttpCodec[R, A]): Chunk[Atom[_, _]] =
    in match {
      case Combine(left, right, _)       => flattenedAtoms(left) ++ flattenedAtoms(right)
      case atom: Atom[_, _]              => Chunk.single(atom)
      case map: TransformOrFail[_, _, _] => flattenedAtoms(map.api)
      case Annotated(api, _)             => flattenedAtoms(api)
      case Empty                         => Chunk.empty
      case Halt                          => Chunk.empty
      case Fallback(_, _, _, _) => throw new UnsupportedOperationException("Cannot handle fallback at this level")
    }
}
