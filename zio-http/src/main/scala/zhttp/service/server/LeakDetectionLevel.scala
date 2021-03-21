package zhttp.service.server

import io.netty.util.{ResourceLeakDetector => JResourceLeakDetector}

sealed trait LeakDetectionLevel { self =>
  def jResourceLeakDetectionLevel: JResourceLeakDetector.Level = self match {
    case LeakDetectionLevel.DISABLED => JResourceLeakDetector.Level.DISABLED
    case LeakDetectionLevel.SIMPLE   => JResourceLeakDetector.Level.SIMPLE
    case LeakDetectionLevel.ADVANCED => JResourceLeakDetector.Level.ADVANCED
    case LeakDetectionLevel.PARANOID => JResourceLeakDetector.Level.PARANOID
  }
}

object LeakDetectionLevel {
  case object DISABLED extends LeakDetectionLevel
  case object SIMPLE   extends LeakDetectionLevel
  case object ADVANCED extends LeakDetectionLevel
  case object PARANOID extends LeakDetectionLevel
}
