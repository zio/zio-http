/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.internal

private[http] object CharSequenceExtensions {

  def equals(left: CharSequence, right: CharSequence, caseMode: CaseMode = CaseMode.Sensitive): Boolean =
    left.length == right.length && compare(left, right, caseMode) == 0

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

      caseMode match {
        case CaseMode.Sensitive   =>
          var i = 0
          while (i < leftLength && i < rightLength) {
            val leftChar  = left.charAt(i)
            val rightChar = right.charAt(i)
            if (leftChar != rightChar) {
              return leftChar - rightChar
            }
            i += 1
          }
        case CaseMode.Insensitive =>
          var i = 0
          while (i < leftLength && i < rightLength) {
            val leftChar  = left.charAt(i)
            val rightChar = right.charAt(i)
            if (leftChar != rightChar) {
              val lLower = leftChar.toLower
              val rLower = rightChar.toLower
              if (lLower != rLower) {
                return lLower - rLower
              }
            }
            i += 1
          }
      }
      leftLength.compare(rightLength)
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
