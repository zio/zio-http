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

import java.net.InetSocketAddress

import zio.{Duration, Scope, Trace, ZIO}

/**
 * A pool of client connections. The client driver gets a connection for each request with `get` and gives a broken one back with `invalidate`.
 */
trait ConnectionPool[Connection] {

  /**
   * Returns a connection to `location`, taking one from the pool when it has an open one. The connection is released when the `Scope` closes.
   *
   * @param location
   *   the destination: scheme, host and port
   * @param proxy
   *   the proxy to connect through, if any
   * @param sslOptions
   *   the TLS settings, used when the scheme is secure
   * @param maxInitialLineLength
   *   the maximum length of the initial line of a response
   * @param maxHeaderSize
   *   the maximum size of the response headers
   * @param decompression
   *   the response decompression settings
   * @param idleTimeout
   *   the read timeout of the connection, if any
   * @param connectionTimeout
   *   the timeout for opening the connection, if any
   * @param localAddress
   *   the local address to bind a new connection to
   */
  def get(
    location: URL.Location.Absolute,
    proxy: Option[Proxy],
    sslOptions: ClientSSLConfig,
    maxInitialLineLength: Int,
    maxHeaderSize: Int,
    decompression: Decompression,
    idleTimeout: Option[Duration],
    connectionTimeout: Option[Duration],
    localAddress: Option[InetSocketAddress] = None,
  )(implicit trace: Trace): ZIO[Scope, Throwable, Connection]

  /** Removes a connection that is no longer usable from the pool. */
  def invalidate(connection: Connection)(implicit trace: Trace): ZIO[Any, Nothing, Unit]

  /** Returns `true` when connections are kept alive for reuse. */
  def enableKeepAlive: Boolean
}
