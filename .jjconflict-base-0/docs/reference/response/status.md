---
id: status
title: Status Codes
---

HTTP status codes are standard response codes given by web services on the Internet. The codes help identify the cause of the problem when a web page or other resource does not load properly.

In ZIO HTTP, `Status` is a sealed trait that represents the status code of an HTTP response. It is designed to encode HTTP status codes and headers along with custom methods and headers (as defined in [RFC2615](https://datatracker.ietf.org/doc/html/rfc2616)).

The ZIO HTTP has a predefined set of constructors for `Response` for the most common status codes.
To create a `Response` with a status code, we can use the predefined constructors for the most common status codes, like `Response.ok`, `Response.notFound`, `Response.forbidden`, etc.

But, if we need a specific status code, we can pass one of the following status codes to the `Response.status` constructor:

```scala mdoc
import zio.http._

Response.status(Status.PermanentRedirect)
```

Here is a list of status codes and their descriptions:

| Category      | Status Class Name      | Description                                                      | Code Range  |
|---------------|------------------------|------------------------------------------------------------------|-------------|
| Informational | `Continue`             | The server has received the request headers, and the client should proceed to send the request body. | 100         |
|               | `SwitchingProtocols`   | The server is switching protocols according to the client's request. | 101         |
|               | `Processing`           | The server is processing the request but has not completed it yet. | 102         |
| Success       | `Ok`                   | The request has succeeded.                                       | 200         |
|               | `Created`              | The request has been fulfilled and resulted in a new resource being created. | 201         |
|               | `Accepted`             | The request has been accepted for processing but has not been completed. | 202         |
|               | `NonAuthoritativeInformation` | The returned meta-information in the entity-header is not the definitive set available from the origin server. | 203         |
|               | `NoContent`            | The server successfully processed the request and is not returning any content. | 204         |
|               | `ResetContent`         | The server successfully processed the request but is not returning any content. | 205         |
|               | `PartialContent`       | The server successfully processed only part of the request.        | 206         |
|               | `MultiStatus`          | The server has multiple status codes for different independent operations. | 207         |
| Redirection   | `MultipleChoices`      | The request has multiple possible responses and the user or user agent can choose the correct one. | 300         |
|               | `MovedPermanently`     | The requested page has been permanently moved to a new location.   | 301         |
|               | `Found`                | The requested page has been found but is temporarily located at another URI. | 302         |
|               | `SeeOther`             | The response to the request can be found under another URI using a GET method. | 303         |
|               | `NotModified`          | The resource has not been modified since the last request.          | 304         |
|               | `UseProxy`             | The requested resource is available only through a proxy, whose address is provided in the response. | 305         |
|               | `TemporaryRedirect`    | The requested resource has been temporarily moved to a different URI. | 307         |
|               | `PermanentRedirect`    | The requested resource has been permanently moved to a different URI. | 308         |
| Client Error  | `BadRequest`           | The request cannot be fulfilled due to bad syntax.                 | 400         |
|               | `Unauthorized`         | The request requires user authentication.                         | 401         |
|               | `PaymentRequired`      | Reserved for future use.                                         | 402         |
|               | `Forbidden`            | The server understood the request, but is refusing to fulfill it. | 403         |
|               | `NotFound`             | The requested resource could not be found but may be available again in the future. | 404         |
|               | `MethodNotAllowed`     | The method specified in the request is not allowed for the resource identified by the request URI. | 405         |
|               | `NotAcceptable`        | The resource identified by the request is only capable of generating response entities that have content characteristics not acceptable according to the accept headers sent in the request. | 406         |
|               | `ProxyAuthenticationRequired` | Similar to 401 but authentication is needed for accessing the proxy. | 407         |
|               | `RequestTimeout`       | The server timed out waiting for the request.                      | 408         |
|               | `Conflict`             | Indicates that the request could not be processed because of conflict in the request. | 409         |
|               | `Gone`                 | The requested resource is no longer available at the server and no forwarding address is known. | 410         |
|               | `LengthRequired`       | The server refuses to accept the request without a defined Content-Length. | 411         |
|               | `PreconditionFailed`   | The precondition given in one or more of the request-header fields evaluated to false when it was tested on the server. | 412         |
|               | `RequestEntityTooLarge` | The server is refusing to service the request because the request entity is larger than the server is willing or able to process. | 413         |
|               | `RequestUriTooLong`    | The server is refusing to interpret the request because the Request-URI is longer than the server is willing to interpret. | 414         |
|               | `UnsupportedMediaType` | The request entity has a media type that the server or resource does not support. | 415         |
|               | `RequestedRangeNotSatisfiable` | The client has asked for a portion of the file, but the server cannot supply that portion. | 416         |
|               | `ExpectationFailed`    | The server cannot meet the requirements of the Expect request-header field. | 417         |
|               | `MisdirectedRequest`   | The request was directed at a server that is not able to produce a response. | 421         |
|               | `UnprocessableEntity`  | The request was well-formed but was unable to be followed due to semantic errors. | 422         |
|               | `Locked`               | The resource that is being accessed is locked.                     | 423         |
|               | `FailedDependency`     | The method could not be performed on the resource because the server is unable to store the representation needed to successfully complete the request. | 424         |
|               | `UnorderedCollection`  | The server is not able to produce a response representing the current state of the target resource due to some internal error. | 425         |
|               | `UpgradeRequired`      | The server refuses to perform the request using the current protocol but might be willing to do so after the client upgrades to a different protocol. | 426         |
|               | `PreconditionRequired` | The origin server requires the request to be conditional.          | 428         |
|               | `TooManyRequests`      | The user has sent too many requests in a given amount of time ("rate limiting"). | 429         |
|               | `RequestHeaderFieldsTooLarge` | The server is unwilling to process the request because either an individual header field or all the header fields collectively are too large. | 431         |
| Server Error  | `InternalServerError` | A generic error message, given when an unexpected condition was encountered and no more specific message is suitable. | 500         |
|               | `NotImplemented`       | The server either does not recognize the request method, or it lacks the ability to fulfill the request. | 501         |
|               | `BadGateway`           | The server was acting as a gateway or proxy and received an invalid response from the upstream server. | 502         |
|               | `ServiceUnavailable`   | The server is currently unable to handle the request due to a temporary overload or maintenance of the server. | 503         |
|               | `GatewayTimeout`       | The server, while acting as a gateway or proxy, did not receive a timely response from the upstream server specified by the URI. | 504         |
|               | `HttpVersionNotSupported` | The server does not support the HTTP protocol version that was used in the request. | 505         |
|               | `VariantAlsoNegotiates` | Transparent content negotiation for the request results in a circular reference. | 506         |
|               | `InsufficientStorage`  | The server is unable to store the representation needed to complete the request. | 507         |
|               | `NotExtended`          | Further extensions to the request are required for the server to fulfill it. | 510         |
|               | `NetworkAuthenticationRequired` | The client needs to authenticate to gain network access.            | 511         |
