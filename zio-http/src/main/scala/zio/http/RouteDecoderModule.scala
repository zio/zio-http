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

import java.time.{LocalDate, LocalDateTime}
import java.util.UUID

/**
 * Instead of using just `String` as path params, using the RouteDecoderModule
 * we can extract and converted params into a specific type also.
 *
 * ```scala
 * Http.collect[Request] {
 *   case GET -> Root / "user" / int(id) => Response.text("User id requested: \${id}")
 *   case GET -> Root / "user" / name    => Response.text("User name requested: \${name}")
 * }
 * ```
 *
 * If the request looks like `GET /user/100` then it would match the first case.
 * This is because internally the `id` param can be decoded into an `Int`. If a
 * request of the form `GET /user/zio` is made, in that case the second case is
 * matched.
 */

trait RouteDecoderModule {
  abstract class RouteDecode[A](f: String => A) {
    def unapply(a: String): Option[A] =
      try {
        Option(f(a))
      } catch {
        case _: Throwable => None
      }
  }

  object boolean extends RouteDecode(_.toBoolean)
  object byte    extends RouteDecode(_.toByte)
  object short   extends RouteDecode(_.toShort)
  object int     extends RouteDecode(_.toInt)
  object long    extends RouteDecode(_.toLong)
  object float   extends RouteDecode(_.toFloat)
  object double  extends RouteDecode(_.toDouble)
  object uuid    extends RouteDecode(str => UUID.fromString(str))
  object date    extends RouteDecode(str => LocalDate.parse(str))
  object time    extends RouteDecode(str => LocalDateTime.parse(str))
}
