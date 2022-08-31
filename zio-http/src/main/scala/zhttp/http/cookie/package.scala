package zhttp.http

package object cookie {
  type RequestCookie  = Cookie[Cookie.Request]
  type ResponseCookie = Cookie[Cookie.Response]
}
