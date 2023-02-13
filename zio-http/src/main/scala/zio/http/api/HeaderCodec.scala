package zio.http.api

import zio.stacktracer.TracingImplicits.disableAutoTrace // scalafix:ok;

object HeaderCodec extends HeaderCodecs
