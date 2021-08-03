package zhttp.http

import io.netty.handler.codec.http.HttpResponseStatus

sealed trait Status { self =>
  def toJHttpStatus: HttpResponseStatus = self match {
    case Status.CONTINUE                        => HttpResponseStatus.CONTINUE                        // 100
    case Status.SWITCHING_PROTOCOLS             => HttpResponseStatus.SWITCHING_PROTOCOLS             // 101
    case Status.PROCESSING                      => HttpResponseStatus.PROCESSING                      // 102
    case Status.OK                              => HttpResponseStatus.OK                              // 200
    case Status.CREATED                         => HttpResponseStatus.CREATED                         // 201
    case Status.ACCEPTED                        => HttpResponseStatus.ACCEPTED                        // 202
    case Status.NON_AUTHORITATIVE_INFORMATION   => HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   // 203
    case Status.NO_CONTENT                      => HttpResponseStatus.NO_CONTENT                      // 204
    case Status.RESET_CONTENT                   => HttpResponseStatus.RESET_CONTENT                   // 205
    case Status.PARTIAL_CONTENT                 => HttpResponseStatus.PARTIAL_CONTENT                 // 206
    case Status.MULTI_STATUS                    => HttpResponseStatus.MULTI_STATUS                    // 207
    case Status.MULTIPLE_CHOICES                => HttpResponseStatus.MULTIPLE_CHOICES                // 300
    case Status.MOVED_PERMANENTLY               => HttpResponseStatus.MOVED_PERMANENTLY               // 301
    case Status.FOUND                           => HttpResponseStatus.FOUND                           // 302
    case Status.SEE_OTHER                       => HttpResponseStatus.SEE_OTHER                       // 303
    case Status.NOT_MODIFIED                    => HttpResponseStatus.NOT_MODIFIED                    // 304
    case Status.USE_PROXY                       => HttpResponseStatus.USE_PROXY                       // 305
    case Status.TEMPORARY_REDIRECT              => HttpResponseStatus.TEMPORARY_REDIRECT              // 307
    case Status.PERMANENT_REDIRECT              => HttpResponseStatus.PERMANENT_REDIRECT              // 308
    case Status.BAD_REQUEST                     => HttpResponseStatus.BAD_REQUEST                     // 400
    case Status.UNAUTHORIZED                    => HttpResponseStatus.UNAUTHORIZED                    // 401
    case Status.PAYMENT_REQUIRED                => HttpResponseStatus.PAYMENT_REQUIRED                // 402
    case Status.FORBIDDEN                       => HttpResponseStatus.FORBIDDEN                       // 403
    case Status.NOT_FOUND                       => HttpResponseStatus.NOT_FOUND                       // 404
    case Status.METHOD_NOT_ALLOWED              => HttpResponseStatus.METHOD_NOT_ALLOWED              // 405
    case Status.NOT_ACCEPTABLE                  => HttpResponseStatus.NOT_ACCEPTABLE                  // 406
    case Status.PROXY_AUTHENTICATION_REQUIRED   => HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   // 407
    case Status.REQUEST_TIMEOUT                 => HttpResponseStatus.REQUEST_TIMEOUT                 // 408
    case Status.CONFLICT                        => HttpResponseStatus.CONFLICT                        // 409
    case Status.GONE                            => HttpResponseStatus.GONE                            // 410
    case Status.LENGTH_REQUIRED                 => HttpResponseStatus.LENGTH_REQUIRED                 // 411
    case Status.PRECONDITION_FAILED             => HttpResponseStatus.PRECONDITION_FAILED             // 412
    case Status.REQUEST_ENTITY_TOO_LARGE        => HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        // 413
    case Status.REQUEST_URI_TOO_LONG            => HttpResponseStatus.REQUEST_URI_TOO_LONG            // 414
    case Status.UNSUPPORTED_MEDIA_TYPE          => HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          // 415
    case Status.REQUESTED_RANGE_NOT_SATISFIABLE => HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE // 416
    case Status.EXPECTATION_FAILED              => HttpResponseStatus.EXPECTATION_FAILED              // 417
    case Status.MISDIRECTED_REQUEST             => HttpResponseStatus.MISDIRECTED_REQUEST             // 421
    case Status.UNPROCESSABLE_ENTITY            => HttpResponseStatus.UNPROCESSABLE_ENTITY            // 422
    case Status.LOCKED                          => HttpResponseStatus.LOCKED                          // 423
    case Status.FAILED_DEPENDENCY               => HttpResponseStatus.FAILED_DEPENDENCY               // 424
    case Status.UNORDERED_COLLECTION            => HttpResponseStatus.UNORDERED_COLLECTION            // 425
    case Status.UPGRADE_REQUIRED                => HttpResponseStatus.UPGRADE_REQUIRED                // 426
    case Status.PRECONDITION_REQUIRED           => HttpResponseStatus.PRECONDITION_REQUIRED           // 428
    case Status.TOO_MANY_REQUESTS               => HttpResponseStatus.TOO_MANY_REQUESTS               // 429
    case Status.REQUEST_HEADER_FIELDS_TOO_LARGE => HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE // 431
    case Status.INTERNAL_SERVER_ERROR           => HttpResponseStatus.INTERNAL_SERVER_ERROR           // 500
    case Status.NOT_IMPLEMENTED                 => HttpResponseStatus.NOT_IMPLEMENTED                 // 501
    case Status.BAD_GATEWAY                     => HttpResponseStatus.BAD_GATEWAY                     // 502
    case Status.SERVICE_UNAVAILABLE             => HttpResponseStatus.SERVICE_UNAVAILABLE             // 503
    case Status.GATEWAY_TIMEOUT                 => HttpResponseStatus.GATEWAY_TIMEOUT                 // 504
    case Status.HTTP_VERSION_NOT_SUPPORTED      => HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      // 505
    case Status.VARIANT_ALSO_NEGOTIATES         => HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         // 506
    case Status.INSUFFICIENT_STORAGE            => HttpResponseStatus.INSUFFICIENT_STORAGE            // 507
    case Status.NOT_EXTENDED                    => HttpResponseStatus.NOT_EXTENDED                    // 510
    case Status.NETWORK_AUTHENTICATION_REQUIRED => HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED // 511
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

  def fromHttpResponseStatus(jStatus: HttpResponseStatus): Status = (jStatus: @unchecked) match {
    case HttpResponseStatus.CONTINUE                        => Status.CONTINUE
    case HttpResponseStatus.SWITCHING_PROTOCOLS             => Status.SWITCHING_PROTOCOLS
    case HttpResponseStatus.PROCESSING                      => Status.PROCESSING
    case HttpResponseStatus.OK                              => Status.OK
    case HttpResponseStatus.CREATED                         => Status.CREATED
    case HttpResponseStatus.ACCEPTED                        => Status.ACCEPTED
    case HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   => Status.NON_AUTHORITATIVE_INFORMATION
    case HttpResponseStatus.NO_CONTENT                      => Status.NO_CONTENT
    case HttpResponseStatus.RESET_CONTENT                   => Status.RESET_CONTENT
    case HttpResponseStatus.PARTIAL_CONTENT                 => Status.PARTIAL_CONTENT
    case HttpResponseStatus.MULTI_STATUS                    => Status.MULTI_STATUS
    case HttpResponseStatus.MULTIPLE_CHOICES                => Status.MULTIPLE_CHOICES
    case HttpResponseStatus.MOVED_PERMANENTLY               => Status.MOVED_PERMANENTLY
    case HttpResponseStatus.FOUND                           => Status.FOUND
    case HttpResponseStatus.SEE_OTHER                       => Status.SEE_OTHER
    case HttpResponseStatus.NOT_MODIFIED                    => Status.NOT_MODIFIED
    case HttpResponseStatus.USE_PROXY                       => Status.USE_PROXY
    case HttpResponseStatus.TEMPORARY_REDIRECT              => Status.TEMPORARY_REDIRECT
    case HttpResponseStatus.PERMANENT_REDIRECT              => Status.PERMANENT_REDIRECT
    case HttpResponseStatus.BAD_REQUEST                     => Status.BAD_REQUEST
    case HttpResponseStatus.UNAUTHORIZED                    => Status.UNAUTHORIZED
    case HttpResponseStatus.PAYMENT_REQUIRED                => Status.PAYMENT_REQUIRED
    case HttpResponseStatus.FORBIDDEN                       => Status.FORBIDDEN
    case HttpResponseStatus.NOT_FOUND                       => Status.NOT_FOUND
    case HttpResponseStatus.METHOD_NOT_ALLOWED              => Status.METHOD_NOT_ALLOWED
    case HttpResponseStatus.NOT_ACCEPTABLE                  => Status.NOT_ACCEPTABLE
    case HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   => Status.PROXY_AUTHENTICATION_REQUIRED
    case HttpResponseStatus.REQUEST_TIMEOUT                 => Status.REQUEST_TIMEOUT
    case HttpResponseStatus.CONFLICT                        => Status.CONFLICT
    case HttpResponseStatus.GONE                            => Status.GONE
    case HttpResponseStatus.LENGTH_REQUIRED                 => Status.LENGTH_REQUIRED
    case HttpResponseStatus.PRECONDITION_FAILED             => Status.PRECONDITION_FAILED
    case HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        => Status.REQUEST_ENTITY_TOO_LARGE
    case HttpResponseStatus.REQUEST_URI_TOO_LONG            => Status.REQUEST_URI_TOO_LONG
    case HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          => Status.UNSUPPORTED_MEDIA_TYPE
    case HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE => Status.REQUESTED_RANGE_NOT_SATISFIABLE
    case HttpResponseStatus.EXPECTATION_FAILED              => Status.EXPECTATION_FAILED
    case HttpResponseStatus.MISDIRECTED_REQUEST             => Status.MISDIRECTED_REQUEST
    case HttpResponseStatus.UNPROCESSABLE_ENTITY            => Status.UNPROCESSABLE_ENTITY
    case HttpResponseStatus.LOCKED                          => Status.LOCKED
    case HttpResponseStatus.FAILED_DEPENDENCY               => Status.FAILED_DEPENDENCY
    case HttpResponseStatus.UNORDERED_COLLECTION            => Status.UNORDERED_COLLECTION
    case HttpResponseStatus.UPGRADE_REQUIRED                => Status.UPGRADE_REQUIRED
    case HttpResponseStatus.PRECONDITION_REQUIRED           => Status.PRECONDITION_REQUIRED
    case HttpResponseStatus.TOO_MANY_REQUESTS               => Status.TOO_MANY_REQUESTS
    case HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE => Status.REQUEST_HEADER_FIELDS_TOO_LARGE
    case HttpResponseStatus.INTERNAL_SERVER_ERROR           => Status.INTERNAL_SERVER_ERROR
    case HttpResponseStatus.NOT_IMPLEMENTED                 => Status.NOT_IMPLEMENTED
    case HttpResponseStatus.BAD_GATEWAY                     => Status.BAD_GATEWAY
    case HttpResponseStatus.SERVICE_UNAVAILABLE             => Status.SERVICE_UNAVAILABLE
    case HttpResponseStatus.GATEWAY_TIMEOUT                 => Status.GATEWAY_TIMEOUT
    case HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      => Status.HTTP_VERSION_NOT_SUPPORTED
    case HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         => Status.VARIANT_ALSO_NEGOTIATES
    case HttpResponseStatus.INSUFFICIENT_STORAGE            => Status.INSUFFICIENT_STORAGE
    case HttpResponseStatus.NOT_EXTENDED                    => Status.NOT_EXTENDED
    case HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED => Status.NETWORK_AUTHENTICATION_REQUIRED
  }
}
