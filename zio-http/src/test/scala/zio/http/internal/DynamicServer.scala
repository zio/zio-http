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

package zio.http.internal

import java.util.UUID

import zio._

import zio.http._
import zio.http.internal.DynamicServer.Id

sealed trait DynamicServer {
  def add(app: HttpApp[Any]): UIO[Id]

  def get(id: Id): UIO[Option[HttpApp[Any]]]

  def port: ZIO[Any, Nothing, Int]

  def setStart(n: Server): UIO[Boolean]

  def start: IO[Nothing, Server]
}

object DynamicServer {

  type Id = String

  val APP_ID = "X-APP_ID"

  def app(dynamicServer: DynamicServer): RequestHandler[Any, Response] =
    Handler.fromFunctionHandler[Request] { (req: Request) =>
      Handler
        .fromZIO(req.rawHeader(APP_ID) match {
          case Some(id) =>
            get(id)
              .provideEnvironment(ZEnvironment(dynamicServer))
              .map(_.map(_.toHandler))
              .map(_.getOrElse(Handler.notFound))
          case None     =>
            ZIO.succeed(Handler.notFound)
        })
        .flatten
    }

  def baseURL(scheme: Scheme): ZIO[DynamicServer, Nothing, String] =
    port.map(port => s"${scheme.encode}://localhost:$port")

  def deploy[R](app: HttpApp[R]): ZIO[DynamicServer with R, Nothing, String] =
    for {
      env <- ZIO.environment[R]
      id  <- ZIO.environmentWithZIO[DynamicServer](_.get.add(app.provideEnvironment(env)))
    } yield id

  def get(id: Id): ZIO[DynamicServer, Nothing, Option[HttpApp[Any]]] =
    ZIO.environmentWithZIO[DynamicServer](_.get.get(id))

  def httpURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.HTTP)

  def live: ZLayer[Any, Nothing, DynamicServer] =
    ZLayer {
      for {
        ref <- Ref.make(Map.empty[Id, HttpApp[Any]])
        pr  <- Promise.make[Nothing, Server]
      } yield new Live(ref, pr)
    }

  def port: ZIO[DynamicServer, Nothing, Int] = ZIO.environmentWithZIO[DynamicServer](_.get.port)

  def setStart(s: Server): ZIO[DynamicServer, Nothing, Boolean] =
    ZIO.environmentWithZIO[DynamicServer](_.get.setStart(s))

  def start: ZIO[DynamicServer, Nothing, Server] = ZIO.environmentWithZIO[DynamicServer](_.get.start)

  def wsURL: ZIO[DynamicServer, Nothing, String] = baseURL(Scheme.WS)

  final class Live(ref: Ref[Map[Id, HttpApp[Any]]], pr: Promise[Nothing, Server]) extends DynamicServer {
    def add(app: HttpApp[Any]): UIO[Id] = for {
      id <- ZIO.succeed(UUID.randomUUID().toString)
      _  <- ref.update(map => map + (id -> app))
    } yield id

    def get(id: Id): UIO[Option[HttpApp[Any]]] = ref.get.map(_.get(id))

    def port: ZIO[Any, Nothing, Int] = start.map(_.port)

    def setStart(s: Server): UIO[Boolean] = pr.complete(ZIO.attempt(s).orDie)

    def start: IO[Nothing, Server] = pr.await
  }
}
