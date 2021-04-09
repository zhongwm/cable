/*
 * Copyright 2020-2021, Wenming Zhong
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 * may be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/* Written by Wenming Zhong */

package zhongwm.cable.parser

import atto.Atto._

trait CommonParsers {
  val singleQuotedStringP = char('\'') ~> many(notChar('\'')) <~ char('\'')
  val doubleQuotedStringP = char('"') ~> many(notChar('"')) <~ char('"')
  val nonEmptyStrTillEndP =
    many(charRange(32.toChar to 126.toChar)) <~ either(char('\n'), endOfChunk)
  val horizontalNonQuotedNonWhitespace = charRange(33.toChar to 126.toChar)
  val identifierCharP = either(letterOrDigit, oneOf("-_.")).map(_.fold(a=>a, b=>b))
  val identifierP = many1(identifierCharP)
  val lineComment             = skipMany(horizontalWhitespace) ~> char('#') <~ many(notChar('\n')) <~ char('\n')
  val emptyLineP              = skipMany(horizontalWhitespace) <~ char('\n')
  val emptyOrJustCommentLineP = either(emptyLineP, lineComment)
  val spaceOrTab              = many1(either(spaceChar, char('\t')))

}
