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

package zio.http.model.headers

import zio.http.model.Headers.Header

/**
 * A trait that provides a ton of powerful operators when extended. Any type
 * that extends HeaderExtension needs to implement the two methods viz.
 * `getHeaders` and `updateHeaders`. All other operators are built on top these
 * two methods.
 */
private[zio] trait HeaderIterable extends Iterable[Header] {}
