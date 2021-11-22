package zhttp

import zhttp.http._
import zhttp.http.middleware.HttpMiddleware.basicAuth

object AllApis {
  def api = HttpApp.collect {
    case Method.GET -> !!          => Response.ok
    case Method.POST -> !!         => Response.status(Status.CREATED)
    case Method.GET -> !! / "boom" => Response.status(Status.INTERNAL_SERVER_ERROR)
  }

  def basicAuthApi = HttpApp.collect { case Method.GET -> !! / "auth" =>
    Response.ok
  } @@ basicAuth("root", "changeme")

  def contentDecoderApi = HttpApp.collectM { case req @ Method.GET -> !! / "contentdecoder" / "text" =>
    req.getBody(ContentDecoder.text).map { content =>
      Response(status = Status.OK, data = HttpData.fromText(content))
    }
  }
  def apply()           = api +++ basicAuthApi +++ contentDecoderApi

}
