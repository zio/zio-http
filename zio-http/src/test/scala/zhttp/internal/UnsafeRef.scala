package zhttp.internal

import zio.UIO

case class UnsafeRef[A](private var count: A) {
  def set(value: A): UIO[Unit] = UIO {
    count = value
  }

  def get: UIO[A] = UIO {
    count
  }
}
