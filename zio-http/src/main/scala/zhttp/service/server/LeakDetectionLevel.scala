package zhttp.service.server

import io.netty.util.ResourceLeakDetector

sealed trait LeakDetectionLevel { self =>
  def jResourceLeakDetectionLevel: ResourceLeakDetector.Level = self match {
    case LeakDetectionLevel.DISABLED => ResourceLeakDetector.Level.DISABLED
    case LeakDetectionLevel.SIMPLE   => ResourceLeakDetector.Level.SIMPLE
    case LeakDetectionLevel.ADVANCED => ResourceLeakDetector.Level.ADVANCED
    case LeakDetectionLevel.PARANOID => ResourceLeakDetector.Level.PARANOID
  }
}

object LeakDetectionLevel {
  case object DISABLED extends LeakDetectionLevel
  case object SIMPLE   extends LeakDetectionLevel
  case object ADVANCED extends LeakDetectionLevel
  case object PARANOID extends LeakDetectionLevel
}
