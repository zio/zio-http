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

package zio

package object http {

  /**
   * A smart constructor that attempts to construct a handler from the specified
   * value. If you have difficulty using this function, then please use the
   * constructors on [[zio.http.Handler]] directly.
   */
  def handler[H](handler: => H)(implicit h: ToHandler[H]): Handler[h.Ctx, h.Vars] =
    HandlerMacros.handler(handler)

  type RequestHandler[-Ctx, -Vars] = Handler[Ctx, Vars]

}
