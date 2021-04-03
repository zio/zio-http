package zhttp.http

import io.netty.handler.codec.http.{HttpResponseStatus => JHttpResponseStatus}

sealed trait Status { self =>
  def toJHttpStatus: JHttpResponseStatus = self match {
    case Status.CONTINUE                        => JHttpResponseStatus.CONTINUE                        // 100
    case Status.SWITCHING_PROTOCOLS             => JHttpResponseStatus.SWITCHING_PROTOCOLS             // 101
    case Status.PROCESSING                      => JHttpResponseStatus.PROCESSING                      // 102
    case Status.OK                              => JHttpResponseStatus.OK                              // 200
    case Status.CREATED                         => JHttpResponseStatus.CREATED                         // 201
    case Status.ACCEPTED                        => JHttpResponseStatus.ACCEPTED                        // 202
    case Status.NON_AUTHORITATIVE_INFORMATION   => JHttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   // 203
    case Status.NO_CONTENT                      => JHttpResponseStatus.NO_CONTENT                      // 204
    case Status.RESET_CONTENT                   => JHttpResponseStatus.RESET_CONTENT                   // 205
    case Status.PARTIAL_CONTENT                 => JHttpResponseStatus.PARTIAL_CONTENT                 // 206
    case Status.MULTI_STATUS                    => JHttpResponseStatus.MULTI_STATUS                    // 207
    case Status.MULTIPLE_CHOICES                => JHttpResponseStatus.MULTIPLE_CHOICES                // 300
    case Status.MOVED_PERMANENTLY               => JHttpResponseStatus.MOVED_PERMANENTLY               // 301
    case Status.FOUND                           => JHttpResponseStatus.FOUND                           // 302
    case Status.SEE_OTHER                       => JHttpResponseStatus.SEE_OTHER                       // 303
    case Status.NOT_MODIFIED                    => JHttpResponseStatus.NOT_MODIFIED                    // 304
    case Status.USE_PROXY                       => JHttpResponseStatus.USE_PROXY                       // 305
    case Status.TEMPORARY_REDIRECT              => JHttpResponseStatus.TEMPORARY_REDIRECT              // 307
    case Status.PERMANENT_REDIRECT              => JHttpResponseStatus.PERMANENT_REDIRECT              // 308
    case Status.BAD_REQUEST                     => JHttpResponseStatus.BAD_REQUEST                     // 400
    case Status.UNAUTHORIZED                    => JHttpResponseStatus.UNAUTHORIZED                    // 401
    case Status.PAYMENT_REQUIRED                => JHttpResponseStatus.PAYMENT_REQUIRED                // 402
    case Status.FORBIDDEN                       => JHttpResponseStatus.FORBIDDEN                       // 403
    case Status.NOT_FOUND                       => JHttpResponseStatus.NOT_FOUND                       // 404
    case Status.METHOD_NOT_ALLOWED              => JHttpResponseStatus.METHOD_NOT_ALLOWED              // 405
    case Status.NOT_ACCEPTABLE                  => JHttpResponseStatus.NOT_ACCEPTABLE                  // 406
    case Status.PROXY_AUTHENTICATION_REQUIRED   => JHttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   // 407
    case Status.REQUEST_TIMEOUT                 => JHttpResponseStatus.REQUEST_TIMEOUT                 // 408
    case Status.CONFLICT                        => JHttpResponseStatus.CONFLICT                        // 409
    case Status.GONE                            => JHttpResponseStatus.GONE                            // 410
    case Status.LENGTH_REQUIRED                 => JHttpResponseStatus.LENGTH_REQUIRED                 // 411
    case Status.PRECONDITION_FAILED             => JHttpResponseStatus.PRECONDITION_FAILED             // 412
    case Status.REQUEST_ENTITY_TOO_LARGE        => JHttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        // 413
    case Status.REQUEST_URI_TOO_LONG            => JHttpResponseStatus.REQUEST_URI_TOO_LONG            // 414
    case Status.UNSUPPORTED_MEDIA_TYPE          => JHttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          // 415
    case Status.REQUESTED_RANGE_NOT_SATISFIABLE => JHttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE // 416
    case Status.EXPECTATION_FAILED              => JHttpResponseStatus.EXPECTATION_FAILED              // 417
    case Status.MISDIRECTED_REQUEST             => JHttpResponseStatus.MISDIRECTED_REQUEST             // 421
    case Status.UNPROCESSABLE_ENTITY            => JHttpResponseStatus.UNPROCESSABLE_ENTITY            // 422
    case Status.LOCKED                          => JHttpResponseStatus.LOCKED                          // 423
    case Status.FAILED_DEPENDENCY               => JHttpResponseStatus.FAILED_DEPENDENCY               // 424
    case Status.UNORDERED_COLLECTION            => JHttpResponseStatus.UNORDERED_COLLECTION            // 425
    case Status.UPGRADE_REQUIRED                => JHttpResponseStatus.UPGRADE_REQUIRED                // 426
    case Status.PRECONDITION_REQUIRED           => JHttpResponseStatus.PRECONDITION_REQUIRED           // 428
    case Status.TOO_MANY_REQUESTS               => JHttpResponseStatus.TOO_MANY_REQUESTS               // 429
    case Status.REQUEST_HEADER_FIELDS_TOO_LARGE => JHttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE // 431
    case Status.INTERNAL_SERVER_ERROR           => JHttpResponseStatus.INTERNAL_SERVER_ERROR           // 500
    case Status.NOT_IMPLEMENTED                 => JHttpResponseStatus.NOT_IMPLEMENTED                 // 501
    case Status.BAD_GATEWAY                     => JHttpResponseStatus.BAD_GATEWAY                     // 502
    case Status.SERVICE_UNAVAILABLE             => JHttpResponseStatus.SERVICE_UNAVAILABLE             // 503
    case Status.GATEWAY_TIMEOUT                 => JHttpResponseStatus.GATEWAY_TIMEOUT                 // 504
    case Status.HTTP_VERSION_NOT_SUPPORTED      => JHttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      // 505
    case Status.VARIANT_ALSO_NEGOTIATES         => JHttpResponseStatus.VARIANT_ALSO_NEGOTIATES         // 506
    case Status.INSUFFICIENT_STORAGE            => JHttpResponseStatus.INSUFFICIENT_STORAGE            // 507
    case Status.NOT_EXTENDED                    => JHttpResponseStatus.NOT_EXTENDED                    // 510
    case Status.NETWORK_AUTHENTICATION_REQUIRED => JHttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED // 511
  }
}

object Status {
  case object CONTINUE                        extends Status
  case object SWITCHING_PROTOCOLS             extends Status
  case object PROCESSING                      extends Status
  case object OK                              extends Status
  case object CREATED                         extends Status
  case object ACCEPTED                        extends Status
  case object NON_AUTHORITATIVE_INFORMATION   extends Status
  case object NO_CONTENT                      extends Status
  case object RESET_CONTENT                   extends Status
  case object PARTIAL_CONTENT                 extends Status
  case object MULTI_STATUS                    extends Status
  case object MULTIPLE_CHOICES                extends Status
  case object MOVED_PERMANENTLY               extends Status
  case object FOUND                           extends Status
  case object SEE_OTHER                       extends Status
  case object NOT_MODIFIED                    extends Status
  case object USE_PROXY                       extends Status
  case object TEMPORARY_REDIRECT              extends Status
  case object PERMANENT_REDIRECT              extends Status
  case object BAD_REQUEST                     extends Status
  case object UNAUTHORIZED                    extends Status
  case object PAYMENT_REQUIRED                extends Status
  case object FORBIDDEN                       extends Status
  case object NOT_FOUND                       extends Status
  case object METHOD_NOT_ALLOWED              extends Status
  case object NOT_ACCEPTABLE                  extends Status
  case object PROXY_AUTHENTICATION_REQUIRED   extends Status
  case object REQUEST_TIMEOUT                 extends Status
  case object CONFLICT                        extends Status
  case object GONE                            extends Status
  case object LENGTH_REQUIRED                 extends Status
  case object PRECONDITION_FAILED             extends Status
  case object REQUEST_ENTITY_TOO_LARGE        extends Status
  case object REQUEST_URI_TOO_LONG            extends Status
  case object UNSUPPORTED_MEDIA_TYPE          extends Status
  case object REQUESTED_RANGE_NOT_SATISFIABLE extends Status
  case object EXPECTATION_FAILED              extends Status
  case object MISDIRECTED_REQUEST             extends Status
  case object UNPROCESSABLE_ENTITY            extends Status
  case object LOCKED                          extends Status
  case object FAILED_DEPENDENCY               extends Status
  case object UNORDERED_COLLECTION            extends Status
  case object UPGRADE_REQUIRED                extends Status
  case object PRECONDITION_REQUIRED           extends Status
  case object TOO_MANY_REQUESTS               extends Status
  case object REQUEST_HEADER_FIELDS_TOO_LARGE extends Status
  case object INTERNAL_SERVER_ERROR           extends Status
  case object NOT_IMPLEMENTED                 extends Status
  case object BAD_GATEWAY                     extends Status
  case object SERVICE_UNAVAILABLE             extends Status
  case object GATEWAY_TIMEOUT                 extends Status
  case object HTTP_VERSION_NOT_SUPPORTED      extends Status
  case object VARIANT_ALSO_NEGOTIATES         extends Status
  case object INSUFFICIENT_STORAGE            extends Status
  case object NOT_EXTENDED                    extends Status
  case object NETWORK_AUTHENTICATION_REQUIRED extends Status

  def fromJHttpResponseStatus(jStatus: JHttpResponseStatus): Status = (jStatus: @unchecked) match {
    case JHttpResponseStatus.CONTINUE                        => Status.CONTINUE
    case JHttpResponseStatus.SWITCHING_PROTOCOLS             => Status.SWITCHING_PROTOCOLS
    case JHttpResponseStatus.PROCESSING                      => Status.PROCESSING
    case JHttpResponseStatus.OK                              => Status.OK
    case JHttpResponseStatus.CREATED                         => Status.CREATED
    case JHttpResponseStatus.ACCEPTED                        => Status.ACCEPTED
    case JHttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   => Status.NON_AUTHORITATIVE_INFORMATION
    case JHttpResponseStatus.NO_CONTENT                      => Status.NO_CONTENT
    case JHttpResponseStatus.RESET_CONTENT                   => Status.RESET_CONTENT
    case JHttpResponseStatus.PARTIAL_CONTENT                 => Status.PARTIAL_CONTENT
    case JHttpResponseStatus.MULTI_STATUS                    => Status.MULTI_STATUS
    case JHttpResponseStatus.MULTIPLE_CHOICES                => Status.MULTIPLE_CHOICES
    case JHttpResponseStatus.MOVED_PERMANENTLY               => Status.MOVED_PERMANENTLY
    case JHttpResponseStatus.FOUND                           => Status.FOUND
    case JHttpResponseStatus.SEE_OTHER                       => Status.SEE_OTHER
    case JHttpResponseStatus.NOT_MODIFIED                    => Status.NOT_MODIFIED
    case JHttpResponseStatus.USE_PROXY                       => Status.USE_PROXY
    case JHttpResponseStatus.TEMPORARY_REDIRECT              => Status.TEMPORARY_REDIRECT
    case JHttpResponseStatus.PERMANENT_REDIRECT              => Status.PERMANENT_REDIRECT
    case JHttpResponseStatus.BAD_REQUEST                     => Status.BAD_REQUEST
    case JHttpResponseStatus.UNAUTHORIZED                    => Status.UNAUTHORIZED
    case JHttpResponseStatus.PAYMENT_REQUIRED                => Status.PAYMENT_REQUIRED
    case JHttpResponseStatus.FORBIDDEN                       => Status.FORBIDDEN
    case JHttpResponseStatus.NOT_FOUND                       => Status.NOT_FOUND
    case JHttpResponseStatus.METHOD_NOT_ALLOWED              => Status.METHOD_NOT_ALLOWED
    case JHttpResponseStatus.NOT_ACCEPTABLE                  => Status.NOT_ACCEPTABLE
    case JHttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   => Status.PROXY_AUTHENTICATION_REQUIRED
    case JHttpResponseStatus.REQUEST_TIMEOUT                 => Status.REQUEST_TIMEOUT
    case JHttpResponseStatus.CONFLICT                        => Status.CONFLICT
    case JHttpResponseStatus.GONE                            => Status.GONE
    case JHttpResponseStatus.LENGTH_REQUIRED                 => Status.LENGTH_REQUIRED
    case JHttpResponseStatus.PRECONDITION_FAILED             => Status.PRECONDITION_FAILED
    case JHttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        => Status.REQUEST_ENTITY_TOO_LARGE
    case JHttpResponseStatus.REQUEST_URI_TOO_LONG            => Status.REQUEST_URI_TOO_LONG
    case JHttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          => Status.UNSUPPORTED_MEDIA_TYPE
    case JHttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE => Status.REQUESTED_RANGE_NOT_SATISFIABLE
    case JHttpResponseStatus.EXPECTATION_FAILED              => Status.EXPECTATION_FAILED
    case JHttpResponseStatus.MISDIRECTED_REQUEST             => Status.MISDIRECTED_REQUEST
    case JHttpResponseStatus.UNPROCESSABLE_ENTITY            => Status.UNPROCESSABLE_ENTITY
    case JHttpResponseStatus.LOCKED                          => Status.LOCKED
    case JHttpResponseStatus.FAILED_DEPENDENCY               => Status.FAILED_DEPENDENCY
    case JHttpResponseStatus.UNORDERED_COLLECTION            => Status.UNORDERED_COLLECTION
    case JHttpResponseStatus.UPGRADE_REQUIRED                => Status.UPGRADE_REQUIRED
    case JHttpResponseStatus.PRECONDITION_REQUIRED           => Status.PRECONDITION_REQUIRED
    case JHttpResponseStatus.TOO_MANY_REQUESTS               => Status.TOO_MANY_REQUESTS
    case JHttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE => Status.REQUEST_HEADER_FIELDS_TOO_LARGE
    case JHttpResponseStatus.INTERNAL_SERVER_ERROR           => Status.INTERNAL_SERVER_ERROR
    case JHttpResponseStatus.NOT_IMPLEMENTED                 => Status.NOT_IMPLEMENTED
    case JHttpResponseStatus.BAD_GATEWAY                     => Status.BAD_GATEWAY
    case JHttpResponseStatus.SERVICE_UNAVAILABLE             => Status.SERVICE_UNAVAILABLE
    case JHttpResponseStatus.GATEWAY_TIMEOUT                 => Status.GATEWAY_TIMEOUT
    case JHttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      => Status.HTTP_VERSION_NOT_SUPPORTED
    case JHttpResponseStatus.VARIANT_ALSO_NEGOTIATES         => Status.VARIANT_ALSO_NEGOTIATES
    case JHttpResponseStatus.INSUFFICIENT_STORAGE            => Status.INSUFFICIENT_STORAGE
    case JHttpResponseStatus.NOT_EXTENDED                    => Status.NOT_EXTENDED
    case JHttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED => Status.NETWORK_AUTHENTICATION_REQUIRED
  }
}