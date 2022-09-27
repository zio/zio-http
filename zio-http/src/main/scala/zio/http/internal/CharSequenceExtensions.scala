package zio.http.internal

private[http] object CharSequenceExtensions {

  def equals(left: CharSequence, right: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Boolean =
    if (left eq right) true else compare(left, right, caseMode) == 0

  /**
   * Lexicographically compares two `CharSequence`s.
   *
   * @return
   *   Zero if the arguments are equal. Negative value if left `CharSequence` is
   *   less than the right `CharSequence`. Positive value if left `CharSequence`
   *   is greater than the right `CharSequence`.
   */
  def compare(left: CharSequence, right: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Int = {
    if (left eq right) {
      0
    } else {
      val leftLength  = left.length
      val rightLength = right.length
      var result: Int = 0
      caseMode match {
        case CaseMode.Sensitive   =>
          var i = 0
          while (i < leftLength && i < leftLength && i < rightLength) {
            val leftChar  = left.charAt(i)
            val rightChar = right.charAt(i)
            if (leftChar != rightChar) {
              result = leftChar - rightChar
              i = leftLength
            } else {
              i += 1
            }
          }
        case CaseMode.Insensitive =>
          var i = 0
          while (i < leftLength && i < leftLength && i < rightLength) {
            val leftChar  = left.charAt(i).toLower
            val rightChar = right.charAt(i).toLower
            if (leftChar != rightChar) {
              result = leftChar - rightChar
              i = leftLength
            } else {
              i += 1
            }
          }
      }

      if (result != 0) result else leftLength.compare(rightLength)
    }

  }

  def hashCode(value: CharSequence): Int = {
    val length = value.length()
    var hash   = 0
    var i      = 0
    while (i < length) {
      val character = value.charAt(i)
      hash = 31 * hash + (character & 0xff)
      i += 1
    }
    hash
  }

  def contains(sequence: CharSequence, subsequence: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Boolean = {
    val sequenceLength    = sequence.length
    val subsequenceLength = subsequence.length
    if (sequenceLength >= subsequenceLength && subsequenceLength != 0) {
      val maxPossibleStartIndex = sequenceLength - subsequenceLength
      var result                = false
      var i                     = 0
      caseMode match {
        case CaseMode.Sensitive =>
          val firstCharacter = subsequence.charAt(0)
          while (i <= maxPossibleStartIndex && !result) {
            if (sequence.charAt(i) != firstCharacter) {
              i += 1
            } else {
              var sequenceIndex    = i + 1
              var subsequenceIndex = 1
              while (
                subsequenceIndex < subsequenceLength &&
                sequence.charAt(sequenceIndex) == subsequence.charAt(subsequenceIndex)
              ) {
                sequenceIndex += 1
                subsequenceIndex += 1
              }
              if (sequenceIndex - i == subsequenceLength) {
                result = true
              } else {
                i += 1
              }
            }
          }

        case CaseMode.Insensitive =>
          val firstCharacter = subsequence.charAt(0).toLower
          while (i <= maxPossibleStartIndex && !result) {
            if (sequence.charAt(i).toLower != firstCharacter) {
              i += 1
            } else {
              var sequenceIndex    = i + 1
              var subsequenceIndex = 1
              while (
                subsequenceIndex < subsequenceLength &&
                sequence.charAt(sequenceIndex).toLower == subsequence.charAt(subsequenceIndex).toLower
              ) {
                sequenceIndex += 1
                subsequenceIndex += 1
              }
              if (sequenceIndex - i == subsequenceLength) {
                result = true
              } else {
                i += 1
              }
            }
          }
      }
      result
    } else {
      subsequenceLength == 0
    }
  }
}
