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

package cable.parser

import atto._
import Atto._
import cats.implicits._
import atto.ParseResult.{Done, Fail, Partial}
import scala.util._

class InvalidHostPortUserFormat(msg: String) extends RuntimeException(msg)

case class HostPortUser(username: Option[String], host: String, port: Option[Int]) {
  override def toString = username.map(_ ++ "@").getOrElse("") ++ host ++ port.map(":" ++ _.toString).getOrElse("")
}

trait HostPortUserParser extends CommonParsers {
  def hostPortUserP = for {
    username <- opt(identifierP.map(_.mkString_("")) <~ char('@'))
    hostName <- identifierP.map(_.mkString_(""))
    port <- opt(char(':') ~> int)
  } yield HostPortUser(username, hostName, port)

  /**
   * Many and least 1 hence multi1.
   *
   * Comma separated goes into a hierarchy.
   */
  def multi1HostPortUserP =
    many1(hostPortUserP <~ opt(char(',') ~> many(horizontalWhitespace)))

  def multi1HostPortUserAsProxyJumperP = multi1HostPortUserP map {
    _.foldLeft(None: Option[HostItem])((acc, i) => Some(HostItem(SshConfigHeaderItem(i.host), None, i.port, i.username, None, None, acc, None))).get
  }

  private def parse0[A](p: Parser[A], in: String) = p.parseOnly(in) match {
    case Done(_, r) =>
      Success(r)
    case Fail(i, s, m) =>
      Failure(new InvalidProxyCommandFormat(s"Invalid ProxyJumper format: $i"))
    case Partial(k) =>
      Failure(new InvalidProxyCommandFormat("Invalid ProxyJumper format"))
  }

  def parseMulti1HostPortUserPort(in: String) =
    parse0(multi1HostPortUserP, in)

  def parseProxyJumper(in: String) =
    parse0(multi1HostPortUserAsProxyJumperP, in)
}