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

package zio.http.socket

sealed trait CloseStatus
object CloseStatus {
  case object NormalClosure                          extends CloseStatus
  case object EndpointUnavailable                    extends CloseStatus
  case object ProtocolError                          extends CloseStatus
  case object InvalidMessageType                     extends CloseStatus
  case object InvalidPayloadData                     extends CloseStatus
  case object PolicyViolation                        extends CloseStatus
  case object MessageTooBig                          extends CloseStatus
  case object MandatoryExtension                     extends CloseStatus
  case object InternalServerError                    extends CloseStatus
  case object ServiceRestart                         extends CloseStatus
  case object TryAgainLater                          extends CloseStatus
  case object BadGateway                             extends CloseStatus
  case object Empty                                  extends CloseStatus
  case object AbnormalClosure                        extends CloseStatus
  case object TlsHandshakeFailed                     extends CloseStatus
  final case class Custom(code: Int, reason: String) extends CloseStatus
}
