package zio.http.h2.hpack

import java.nio.charset.StandardCharsets

import scala.collection.mutable.ArrayBuffer

final class DynamicTable(initialMaxSize: Int = 4096) {
  require(initialMaxSize >= 0, s"Dynamic table size must be non-negative: $initialMaxSize")

  private val entries = ArrayBuffer.empty[HeaderField]
  private var size0   = 0
  private var maxSize = initialMaxSize

  def currentSize: Int = size0

  def maximumSize: Int = maxSize

  def length: Int = entries.length

  def add(entry: HeaderField): Unit = {
    val normalized = entry.copy(name = DynamicTable.normalizeName(entry.name))
    val entrySize  = DynamicTable.entrySize(normalized)

    if (entrySize > maxSize) {
      clear()
      ()
    } else {
      evictToFit(entrySize)
      entries.prepend(normalized)
      size0 += entrySize
    }
  }

  def clear(): Unit = {
    entries.clear()
    size0 = 0
  }

  def get(index: Int): Option[HeaderField] = {
    val zeroBased = index - 1
    if (zeroBased >= 0 && zeroBased < entries.length) Some(entries(zeroBased)) else None
  }

  def indexOf(field: HeaderField): Int = {
    val normalizedName = DynamicTable.normalizeName(field.name)
    var i              = 0
    while (i < entries.length) {
      val candidate = entries(i)
      if (candidate.name == normalizedName && candidate.value == field.value) return i + 1
      i += 1
    }
    -1
  }

  def indexOfName(name: String): Int = {
    val normalizedName = DynamicTable.normalizeName(name)
    var i              = 0
    while (i < entries.length) {
      if (entries(i).name == normalizedName) return i + 1
      i += 1
    }
    -1
  }

  def setMaxSize(newMaxSize: Int): Unit = {
    require(newMaxSize >= 0, s"Dynamic table size must be non-negative: $newMaxSize")
    maxSize = newMaxSize
    evictToLimit()
  }

  private def evictToFit(incomingEntrySize: Int): Unit =
    while (size0 > maxSize - incomingEntrySize && entries.nonEmpty) evictOldest()

  private def evictToLimit(): Unit =
    while (size0 > maxSize && entries.nonEmpty) evictOldest()

  private def evictOldest(): Unit = {
    val removed = entries.remove(entries.length - 1)
    size0 -= DynamicTable.entrySize(removed)
  }
}

object DynamicTable {
  private[hpack] def normalizeName(name: String): String = name.toLowerCase(java.util.Locale.ROOT)

  def entrySize(entry: HeaderField): Int = {
    val nameSize  = entry.name.getBytes(StandardCharsets.UTF_8).length
    val valueSize = entry.value.getBytes(StandardCharsets.UTF_8).length
    nameSize + valueSize + 32
  }
}
