package zhttp

import zhttp.core.Nat._

package object core extends AliasModule {
  type ReadableHBuf = HBuf[Two, Direction.Out]
}
