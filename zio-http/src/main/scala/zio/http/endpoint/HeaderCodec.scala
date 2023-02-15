package zio.http.endpoint

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object HeaderCodec extends HeaderCodecs
