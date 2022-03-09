package zhttp.http

import io.netty.handler.codec.http.HttpResponseStatus

sealed trait Status extends Product with Serializable { self =>

  def isInformational: Boolean = code >= 100 && code < 200
  def isSuccess: Boolean       = code >= 200 && code < 300
  def isRedirection: Boolean   = code >= 300 && code < 400
  def isClientError: Boolean   = code >= 400 && code < 500
  def isServerError: Boolean   = code >= 500 && code < 600
  def isError: Boolean         = isClientError | isServerError

  /**
   * Returns self as io.netty.handler.codec.http.HttpResponseStatus.
   */
  def asJava: HttpResponseStatus = self match {
    case Status.Continue                        => HttpResponseStatus.CONTINUE                        // 100
    case Status.Switching_Protocols             => HttpResponseStatus.SWITCHING_PROTOCOLS             // 101
    case Status.Processing                      => HttpResponseStatus.PROCESSING                      // 102
    case Status.Ok                              => HttpResponseStatus.OK                              // 200
    case Status.Created                         => HttpResponseStatus.CREATED                         // 201
    case Status.Accepted                        => HttpResponseStatus.ACCEPTED                        // 202
    case Status.Non_Authoritive_Information     => HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   // 203
    case Status.No_Content                      => HttpResponseStatus.NO_CONTENT                      // 204
    case Status.Reset_Content                   => HttpResponseStatus.RESET_CONTENT                   // 205
    case Status.Partial_Content                 => HttpResponseStatus.PARTIAL_CONTENT                 // 206
    case Status.Multi_Status                    => HttpResponseStatus.MULTI_STATUS                    // 207
    case Status.Multiple_Choices                => HttpResponseStatus.MULTIPLE_CHOICES                // 300
    case Status.Moved_Permanently               => HttpResponseStatus.MOVED_PERMANENTLY               // 301
    case Status.Found                           => HttpResponseStatus.FOUND                           // 302
    case Status.See_Other                       => HttpResponseStatus.SEE_OTHER                       // 303
    case Status.Not_Modified                    => HttpResponseStatus.NOT_MODIFIED                    // 304
    case Status.Use_Proxy                       => HttpResponseStatus.USE_PROXY                       // 305
    case Status.Temporary_Redirect              => HttpResponseStatus.TEMPORARY_REDIRECT              // 307
    case Status.Permanent_Redirect              => HttpResponseStatus.PERMANENT_REDIRECT              // 308
    case Status.Bad_Request                     => HttpResponseStatus.BAD_REQUEST                     // 400
    case Status.Unauthorized                    => HttpResponseStatus.UNAUTHORIZED                    // 401
    case Status.Payment_Required                => HttpResponseStatus.PAYMENT_REQUIRED                // 402
    case Status.Forbidden                       => HttpResponseStatus.FORBIDDEN                       // 403
    case Status.Not_Found                       => HttpResponseStatus.NOT_FOUND                       // 404
    case Status.Method_Not_Allowed              => HttpResponseStatus.METHOD_NOT_ALLOWED              // 405
    case Status.Not_Acceptable                  => HttpResponseStatus.NOT_ACCEPTABLE                  // 406
    case Status.Proxy_Authentication_Required   => HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   // 407
    case Status.Request_Timeout                 => HttpResponseStatus.REQUEST_TIMEOUT                 // 408
    case Status.Conflict                        => HttpResponseStatus.CONFLICT                        // 409
    case Status.Gone                            => HttpResponseStatus.GONE                            // 410
    case Status.Length_Required                 => HttpResponseStatus.LENGTH_REQUIRED                 // 411
    case Status.Precondition_Failed             => HttpResponseStatus.PRECONDITION_FAILED             // 412
    case Status.Request_Entity_Too_Large        => HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        // 413
    case Status.Request_Uri_Too_Long            => HttpResponseStatus.REQUEST_URI_TOO_LONG            // 414
    case Status.Unsupported_Media_Type          => HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          // 415
    case Status.Requested_Range_Not_Satisfiable => HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE // 416
    case Status.Expectation_Failed              => HttpResponseStatus.EXPECTATION_FAILED              // 417
    case Status.Misdirected_Request             => HttpResponseStatus.MISDIRECTED_REQUEST             // 421
    case Status.Unprocessable_Entity            => HttpResponseStatus.UNPROCESSABLE_ENTITY            // 422
    case Status.Locked                          => HttpResponseStatus.LOCKED                          // 423
    case Status.Failed_Dependency               => HttpResponseStatus.FAILED_DEPENDENCY               // 424
    case Status.Unordered_Collection            => HttpResponseStatus.UNORDERED_COLLECTION            // 425
    case Status.Upgrade_Required                => HttpResponseStatus.UPGRADE_REQUIRED                // 426
    case Status.Precondition_Required           => HttpResponseStatus.PRECONDITION_REQUIRED           // 428
    case Status.Too_Many_Requests               => HttpResponseStatus.TOO_MANY_REQUESTS               // 429
    case Status.Request_Header_Fields_Too_Large => HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE // 431
    case Status.Internal_Server_Error           => HttpResponseStatus.INTERNAL_SERVER_ERROR           // 500
    case Status.Not_Implemented                 => HttpResponseStatus.NOT_IMPLEMENTED                 // 501
    case Status.Bad_Gateway                     => HttpResponseStatus.BAD_GATEWAY                     // 502
    case Status.Service_Unavailable             => HttpResponseStatus.SERVICE_UNAVAILABLE             // 503
    case Status.Gateway_Timeout                 => HttpResponseStatus.GATEWAY_TIMEOUT                 // 504
    case Status.Http_Version_Not_Supported      => HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      // 505
    case Status.Variant_Also_Negotiates         => HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         // 506
    case Status.Insufficient_Storage            => HttpResponseStatus.INSUFFICIENT_STORAGE            // 507
    case Status.Not_Extended                    => HttpResponseStatus.NOT_EXTENDED                    // 510
    case Status.Network_Authentication_Required => HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED // 511
    case Status.Custom(code)                    => HttpResponseStatus.valueOf(code)
  }

  /**
   * Returns the status code.
   */
  def code: Int = self.asJava.code()

  /**
   * Returns an HttpApp[Any, Nothing] that responses with this http status code.
   */
  def toApp: UHttpApp = Http.status(self)

  /**
   * Returns a Response with empty data and no headers.
   */
  def toResponse: Response = Response.status(self)
}

object Status {
  case object Continue                            extends Status
  case object Switching_Protocols                 extends Status
  case object Processing                          extends Status
  case object Ok                                  extends Status
  case object Created                             extends Status
  case object Accepted                            extends Status
  case object Non_Authoritive_Information         extends Status
  case object No_Content                          extends Status
  case object Reset_Content                       extends Status
  case object Partial_Content                     extends Status
  case object Multi_Status                        extends Status
  case object Multiple_Choices                    extends Status
  case object Moved_Permanently                   extends Status
  case object Found                               extends Status
  case object See_Other                           extends Status
  case object Not_Modified                        extends Status
  case object Use_Proxy                           extends Status
  case object Temporary_Redirect                  extends Status
  case object Permanent_Redirect                  extends Status
  case object Bad_Request                         extends Status
  case object Unauthorized                        extends Status
  case object Payment_Required                    extends Status
  case object Forbidden                           extends Status
  case object Not_Found                           extends Status
  case object Method_Not_Allowed                  extends Status
  case object Not_Acceptable                      extends Status
  case object Proxy_Authentication_Required       extends Status
  case object Request_Timeout                     extends Status
  case object Conflict                            extends Status
  case object Gone                                extends Status
  case object Length_Required                     extends Status
  case object Precondition_Failed                 extends Status
  case object Request_Entity_Too_Large            extends Status
  case object Request_Uri_Too_Long                extends Status
  case object Unsupported_Media_Type              extends Status
  case object Requested_Range_Not_Satisfiable     extends Status
  case object Expectation_Failed                  extends Status
  case object Misdirected_Request                 extends Status
  case object Unprocessable_Entity                extends Status
  case object Locked                              extends Status
  case object Failed_Dependency                   extends Status
  case object Unordered_Collection                extends Status
  case object Upgrade_Required                    extends Status
  case object Precondition_Required               extends Status
  case object Too_Many_Requests                   extends Status
  case object Request_Header_Fields_Too_Large     extends Status
  case object Internal_Server_Error               extends Status
  case object Not_Implemented                     extends Status
  case object Bad_Gateway                         extends Status
  case object Service_Unavailable                 extends Status
  case object Gateway_Timeout                     extends Status
  case object Http_Version_Not_Supported          extends Status
  case object Variant_Also_Negotiates             extends Status
  case object Insufficient_Storage                extends Status
  case object Not_Extended                        extends Status
  case object Network_Authentication_Required     extends Status
  final case class Custom(override val code: Int) extends Status

  def fromHttpResponseStatus(jStatus: HttpResponseStatus): Status = (jStatus: @unchecked) match {
    case HttpResponseStatus.CONTINUE                        => Status.Continue
    case HttpResponseStatus.SWITCHING_PROTOCOLS             => Status.Switching_Protocols
    case HttpResponseStatus.PROCESSING                      => Status.Processing
    case HttpResponseStatus.OK                              => Status.Ok
    case HttpResponseStatus.CREATED                         => Status.Created
    case HttpResponseStatus.ACCEPTED                        => Status.Accepted
    case HttpResponseStatus.NON_AUTHORITATIVE_INFORMATION   => Status.Non_Authoritive_Information
    case HttpResponseStatus.NO_CONTENT                      => Status.No_Content
    case HttpResponseStatus.RESET_CONTENT                   => Status.Reset_Content
    case HttpResponseStatus.PARTIAL_CONTENT                 => Status.Partial_Content
    case HttpResponseStatus.MULTI_STATUS                    => Status.Multi_Status
    case HttpResponseStatus.MULTIPLE_CHOICES                => Status.Multiple_Choices
    case HttpResponseStatus.MOVED_PERMANENTLY               => Status.Moved_Permanently
    case HttpResponseStatus.FOUND                           => Status.Found
    case HttpResponseStatus.SEE_OTHER                       => Status.See_Other
    case HttpResponseStatus.NOT_MODIFIED                    => Status.Not_Modified
    case HttpResponseStatus.USE_PROXY                       => Status.Use_Proxy
    case HttpResponseStatus.TEMPORARY_REDIRECT              => Status.Temporary_Redirect
    case HttpResponseStatus.PERMANENT_REDIRECT              => Status.Permanent_Redirect
    case HttpResponseStatus.BAD_REQUEST                     => Status.Bad_Request
    case HttpResponseStatus.UNAUTHORIZED                    => Status.Unauthorized
    case HttpResponseStatus.PAYMENT_REQUIRED                => Status.Payment_Required
    case HttpResponseStatus.FORBIDDEN                       => Status.Forbidden
    case HttpResponseStatus.NOT_FOUND                       => Status.Not_Found
    case HttpResponseStatus.METHOD_NOT_ALLOWED              => Status.Method_Not_Allowed
    case HttpResponseStatus.NOT_ACCEPTABLE                  => Status.Not_Acceptable
    case HttpResponseStatus.PROXY_AUTHENTICATION_REQUIRED   => Status.Proxy_Authentication_Required
    case HttpResponseStatus.REQUEST_TIMEOUT                 => Status.Request_Timeout
    case HttpResponseStatus.CONFLICT                        => Status.Conflict
    case HttpResponseStatus.GONE                            => Status.Gone
    case HttpResponseStatus.LENGTH_REQUIRED                 => Status.Length_Required
    case HttpResponseStatus.PRECONDITION_FAILED             => Status.Precondition_Failed
    case HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE        => Status.Request_Entity_Too_Large
    case HttpResponseStatus.REQUEST_URI_TOO_LONG            => Status.Request_Uri_Too_Long
    case HttpResponseStatus.UNSUPPORTED_MEDIA_TYPE          => Status.Unsupported_Media_Type
    case HttpResponseStatus.REQUESTED_RANGE_NOT_SATISFIABLE => Status.Requested_Range_Not_Satisfiable
    case HttpResponseStatus.EXPECTATION_FAILED              => Status.Expectation_Failed
    case HttpResponseStatus.MISDIRECTED_REQUEST             => Status.Misdirected_Request
    case HttpResponseStatus.UNPROCESSABLE_ENTITY            => Status.Unprocessable_Entity
    case HttpResponseStatus.LOCKED                          => Status.Locked
    case HttpResponseStatus.FAILED_DEPENDENCY               => Status.Failed_Dependency
    case HttpResponseStatus.UNORDERED_COLLECTION            => Status.Unordered_Collection
    case HttpResponseStatus.UPGRADE_REQUIRED                => Status.Upgrade_Required
    case HttpResponseStatus.PRECONDITION_REQUIRED           => Status.Precondition_Required
    case HttpResponseStatus.TOO_MANY_REQUESTS               => Status.Too_Many_Requests
    case HttpResponseStatus.REQUEST_HEADER_FIELDS_TOO_LARGE => Status.Request_Header_Fields_Too_Large
    case HttpResponseStatus.INTERNAL_SERVER_ERROR           => Status.Internal_Server_Error
    case HttpResponseStatus.NOT_IMPLEMENTED                 => Status.Not_Implemented
    case HttpResponseStatus.BAD_GATEWAY                     => Status.Bad_Gateway
    case HttpResponseStatus.SERVICE_UNAVAILABLE             => Status.Service_Unavailable
    case HttpResponseStatus.GATEWAY_TIMEOUT                 => Status.Gateway_Timeout
    case HttpResponseStatus.HTTP_VERSION_NOT_SUPPORTED      => Status.Http_Version_Not_Supported
    case HttpResponseStatus.VARIANT_ALSO_NEGOTIATES         => Status.Variant_Also_Negotiates
    case HttpResponseStatus.INSUFFICIENT_STORAGE            => Status.Insufficient_Storage
    case HttpResponseStatus.NOT_EXTENDED                    => Status.Not_Extended
    case HttpResponseStatus.NETWORK_AUTHENTICATION_REQUIRED => Status.Network_Authentication_Required
    case status                                             => Status.Custom(status.code)
  }
}
