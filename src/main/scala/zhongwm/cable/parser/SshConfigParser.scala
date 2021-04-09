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

import atto._
import Atto._
import atto.ParseResult.{Done, Fail, Partial}
import cats.implicits._
import zhongwm.cable.util.SshSecurityKey

import java.nio.file.Paths
import java.security.KeyPair
import scala.util._
import scala.concurrent.duration._

/**
 * Possible extensions in future.
 *
 * eg: Proxy host only by name as. `case class ProxyHostByName(name: String) extends ProxySetting`
 */
sealed trait ProxySetting

case class HostItem(
  name: SshConfigHostHeader,
  hostName: Option[String],
  port: Option[Int],
  username: Option[String],
  password: Option[String],
  privateKey: Option[KeyPair],
  proxyJumper: Option[ProxySetting],
  connectionTimeout: Option[Duration],
) extends ProxySetting

case class SshConfigItemAttr(attrName: String, attrValue: String)
case class SshConfigHostItem(name: SshConfigHostHeader, attrs: List[SshConfigItemAttr])

case class SshConfig(hostItems: List[SshConfigHostItem])

case class SshConfigRegistry(hostItems: Map[String, HostItem], hostPatterns: Map[String, HostItem])

sealed trait SshConfigHostHeader extends Product with Serializable

final case class SshConfigHeaderPattern(pattern: String) extends SshConfigHostHeader {
  override def toString = pattern

  private[cable] val regex = new scala.util.matching.Regex(pattern.replaceAll("\\*", ".*"))

  def matching(in: String): Boolean = regex.matches(in)
}

final case class SshConfigHeaderItem(text: String) extends SshConfigHostHeader {
  override def toString = text
}

class ParseSshConfigFailed(msg: String) extends RuntimeException(msg)

trait SshConfigParser extends CommonParsers with ProxyCommandParser {
  val hostPatternP = string("Host ") ~> (many(identifierCharP) ~ char('*') ~ many(either(identifierCharP, char('*')).map(_.fold(a=>a, b=>b)))).map(abc => abc._1._1.mkString + abc._1._2 + abc._2.mkString)

  val hostTitleLineP = (string("Host ") ~> either(hostPatternP, many1(identifierCharP).map(_.mkString_(""))) <~ many(
    horizontalWhitespace
  ) <~ either(char('\n'), endOfInput)).map(_.fold(a=>SshConfigHeaderPattern(a), b => SshConfigHeaderItem(b)))

  val attrP = for {
    attrName  <- skipMany1(spaceChar) ~> upper ~ identifierP
    _         <- skipMany1(spaceOrTab)
    attrValue <- nonEmptyStrTillEndP
  } yield SshConfigItemAttr(
    (attrName._1 :: attrName._2).mkString_(""),
    attrValue.mkString.trim
  )

  val sshConfigItemP = for {
    hn   <- hostTitleLineP
    attr <- many1(attrP <~ many(emptyOrJustCommentLineP))
  } yield SshConfigHostItem(hn, attr.toList)

  val sshConfigP = for {
    _  <- many(emptyOrJustCommentLineP)
    hi <- many(sshConfigItemP <~ many(emptyOrJustCommentLineP))
  } yield SshConfig(hi)

  def parseUserSshConfig() =
    Using(scala.io.Source.fromFile(Paths.get(sys.props("user.home"), ".ssh", "config").toFile))(_.mkString)
      .flatMap(source1 => Using{scala.io.Source.fromFile("/etc/ssh/ssh_config")}{source1 ++ "\n" ++ _.mkString}.recover(_=>source1))
      .map { sourceStr =>
      sshConfigP.parseOnly(sourceStr) match {
        case Done(input, rst) =>
          rst
        case Fail(_, _, msg) =>
          throw new ParseSshConfigFailed("msg")
        case Partial(k) =>
          throw new RuntimeException("Should not happen.")
      }
    }

  def sshConfigHostItemToHostItem(items: Seq[SshConfigHostItem]) =
    items.map { i =>
      var username: Option[String]           = None
      var host: Option[String]               = None
      var port: Option[Int]                  = None
      var identityFile: Try[Option[KeyPair]] = Success(None)
      var proxy: Try[Option[ProxySetting]]   = Success(None)
      var connTimeout: Try[Option[Duration]] = Success(None)
      i.attrs.foreach { a =>
        a.attrName match {
          case "User" =>
            username = Some(a.attrValue)
          case "HostName" =>
            host = Some(a.attrValue)
          case "Port" =>
            port = Try { a.attrValue.toInt }.toOption
          case "IdentityFile" =>
            identityFile = SshSecurityKey.loadSshKeyPair(a.attrValue).map(Some.apply)
          case "ProxyCommand" =>
            proxy = ProxyCommandParser.parseProxyCommand(a.attrValue).map(Some.apply)
          case "ProxyJump" =>
            proxy = parseProxyJumper(a.attrValue).map(Some.apply)
          case "ConnectTimeout" =>
            connTimeout = if (a.attrValue.nonEmpty) Try{a.attrValue.toInt.seconds}.map(Some.apply) else connTimeout
          case _ =>
          // ignore other options
        }
      }
      (identityFile, proxy, connTimeout).mapN { (identityFileV, proxyV, timeoutV) =>
        HostItem(i.name, host, port, username, None, identityFileV, proxyV, timeoutV)
      }
    }
  
  def buildConfigRegistry(): Try[SshConfigRegistry] = {
    parseUserSshConfig().flatMap(in =>
      sshConfigHostItemToHostItem(in.hostItems).traverse(identity).map{a=>
        val hostItemCollector = collection.mutable.ArrayBuffer.empty[HostItem]
        val hostPatternCollector = collection.mutable.ArrayBuffer.empty[HostItem]
        a.foreach{ hi =>
          hi.name match {
            case _: SshConfigHeaderItem =>
              hostItemCollector += hi
            case _: SshConfigHeaderPattern =>
              hostPatternCollector += hi
          }
        }
        SshConfigRegistry(hostItemCollector.map(i=>i.name.toString -> i).toMap, hostPatternCollector.map(i => i.name.toString -> i).toMap)
      }
    )
  }
}

object SshConfigParser extends SshConfigParser {
  lazy val defaultSshUser = scala.sys.props("user.name")
  lazy val defaultUserSshKeyPair = SshSecurityKey.loadSshKeyPair()
  lazy val configRegistryTry = buildConfigRegistry()
  lazy val configRegistry = configRegistryTry.toOption
}
