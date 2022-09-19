package zio.http.internal

private[http] object CharSequenceExtensions {

  def equals(left: CharSequence, right: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Boolean =
    if (left eq right) true else compare(left, right, caseMode) == 0

  def compare(left: CharSequence, right: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Int = {
    val modify = modifier(caseMode)

    if (left eq right) {
      0
    } else {
      var i      = 0
      val length = Math.min(left.length, right.length)
      while (i < length) {
        val a = modify(left.charAt(i))
        val b = modify(right.charAt(i))
        if (a != b) return a - b

        i += 1
      }

      left.length - right.length
    }
  }

  def hashCode(cs: CharSequence): Int = {
    val length = cs.length()
    var hash   = 0
    var i      = 0
    while (i < length) {
      val ch = cs.charAt(i)
      hash = 31 * hash + (ch & 0xff)
      i += 1
    }
    hash
  }

  def contains(sequence: CharSequence, subsequence: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Boolean = {
    val subsequenceLength = subsequence.length
    if (subsequenceLength == 0) return true
    val sequenceLength    = sequence.length
    if (sequenceLength == 0) return false

    val modify = modifier(caseMode)
    val first  = modify(subsequence.charAt(0))
    val max    = sequenceLength - subsequenceLength
    var i      = 0
    while (i <= max) { // Look for first character.
      if (sequence.charAt(i) != first) {
        while (i <= max && modify(sequence.charAt(i)) != first) {
          i += 1
        }
      }
      // Found first character, now look at the rest of value
      if (i <= max) {
        var j   = i + 1
        val end = j + subsequenceLength - 1
        var k   = 1
        while (j < end && modify(sequence.charAt(j)) == modify(subsequence.charAt(k))) {
          j += 1
          k += 1
        }
        if (j == end) { // Found whole string.
          return true
        }
      }

      i += 1
    }
    false
  }

  private def modifier(caseMode: CaseMode): Char => Char = caseMode match {
    case CaseMode.Sensitive   => identity
    case CaseMode.Insensitive => original => Character.toLowerCase(original)
  }
}
