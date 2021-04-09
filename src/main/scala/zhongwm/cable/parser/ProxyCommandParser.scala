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
import cats.implicits._
import ShellUtils.stripeQuotedString
import atto.ParseResult.{Done, Fail, Partial}
import zhongwm.cable.util.SshSecurityKey

import scala.util._

class InvalidProxyCommandFormat(msg: String) extends RuntimeException(msg)

trait ProxyCommandParser extends CommonParsers with HostPortUserParser {

  val cmdSegmentsP: Parser[List[String]] = for {
    words <- many(choice(many1(horizontalNonQuotedNonWhitespace).map(_.toList), singleQuotedStringP, doubleQuotedStringP) <~ many(horizontalWhitespace))
    _ <- either(char('\n'), endOfChunk)
  } yield words map {_.mkString}

  val interestingKeys = Vector("-l", "-p", "-i", "-J")
  val triggerishSshOptions = "46AaCqfGKkMNnsTtVvXxYy"
  val valueishSshOptions = "oBbcDEeFIiJLlmOopQRS"

  val sshpassInterestingKeys = Vector("-p", "-f")

  case class KVPair(k: String, v: Either[String, Seq[String]])

  val fullyCompactPattern = raw"""\-[46AaCqfGKkMNnsTtVvXxYy]*([oBbcDEeFIiJLlmOopQRS]\w+)""".r

  val partlycompactPattern = raw"""\-[46AaCqfGKkMNnsTtVvXxYy]*([oBbcDEeFIiJLlmOopQRS])""".r

  def isFullyCompactStyle(s: String): Option[String] = s match {
    case fullyCompactPattern(kv) =>
      Some(kv)
    case _ =>
      None
  }

  def isPartlyCompactStyle(s: String): Option[String] = s match {
    case partlycompactPattern(k) =>
      Some(k)
    case _ =>
      None
  }

  def parseProxyCommand(in: String): Try[HostItem] = {
    cmdSegmentsP.parseOnly(in) match {
      case Done(_, rst) =>
        cmdSegmentsToSshConfigHostItems(rst)
      case Fail(_, _, msg) =>
        Failure(new InvalidProxyCommandFormat(msg))
      case Partial(k) =>
        Failure(new InvalidProxyCommandFormat("Should not happen, all input are fed."))
    }
  }

  def cmdSegmentsToSshConfigHostItems(in: Seq[String], state:String="", acc:Try[HostItem]=Success(HostItem(SshConfigHeaderItem(""), None, None, None, None, None, None, None))): Try[HostItem] = {
    in match {
      case s :: ss =>
        state match {
          case "-l" =>
            cmdSegmentsToSshConfigHostItems(ss, "", acc.map{_.copy(username=Some(s))})
          case "-p" =>
            cmdSegmentsToSshConfigHostItems(ss, "", (acc, Try{s.toInt}).mapN((ac, iv) => ac.copy(port=Some(iv))))
          case "-J" =>
            cmdSegmentsToSshConfigHostItems(ss, "",
              (acc, parseProxyJumper(s)).mapN((ac, pj) => ac.copy(proxyJumper = Some(pj))))
            /* val indexOfLastAtSign = s.lastIndexOf('@')
            if (indexOfLastAtSign > 0
              && indexOfLastAtSign < s.length - 1) {
              val un = s.substring(0, indexOfLastAtSign)
              val hn = s.substring(indexOfLastAtSign + 1)
              cmdSegmentsToSshConfigHostItems(ss, "", acc.map(_.copy(proxyJumper = Some(HostItem(hn, Some(hn), None, Some(un), None, None, None, None)))))
            } else {
              cmdSegmentsToSshConfigHostItems(ss, "", acc.map(_.copy(proxyJumper = Some(ProxyHostByName(s)))))
            }*/
          case "-pp" =>
            cmdSegmentsToSshConfigHostItems(ss, "", (acc, stripeQuotedString(s)).mapN((ac, ts) => ac.copy(password=Some(ts))))
          case "-pe" =>
            cmdSegmentsToSshConfigHostItems(ss, "", acc.map(_.copy(password=sys.env.get(s))))
          case "-pf" =>
            cmdSegmentsToSshConfigHostItems(ss, "", (acc, Using(scala.io.Source.fromFile(s)){_.mkString}).mapN((ac, pfs) => ac.copy(password=Some(pfs))))
          case "-i" =>
            cmdSegmentsToSshConfigHostItems(ss, "", (acc, SshSecurityKey.loadSshKeyPair(s)).mapN((ac, kp) => ac.copy(privateKey = Some(kp))))
          case "skip" =>
            cmdSegmentsToSshConfigHostItems(ss, "", acc)
          case ">" =>
            s match {
              case "-p" =>
                cmdSegmentsToSshConfigHostItems(ss, "-pp", acc)
              case "-e" =>
                cmdSegmentsToSshConfigHostItems(ss, "-pe", acc)
              case "-f" =>
                cmdSegmentsToSshConfigHostItems(ss, "-pf", acc)
            }
          case "" =>
            // Try match get some interesting flag start point. fall through this `s match`
            val indexOfLastAtSign = s.lastIndexOf('@')
            val fullyCompacted = isFullyCompactStyle(s)
            val partlyCompacted = isPartlyCompactStyle(s)
            s match {
              case "sshpass"=>
                // expecting a -f, -p, -e or -d, -d is ignored in this case
                cmdSegmentsToSshConfigHostItems(ss, ">", acc)
              case "ssh" =>
                cmdSegmentsToSshConfigHostItems(ss, "", acc)
              case "-l" =>
                cmdSegmentsToSshConfigHostItems(ss, "-l", acc)
              case "-i" =>
                cmdSegmentsToSshConfigHostItems(ss, "-i", acc)
              case "-p" =>
                cmdSegmentsToSshConfigHostItems(ss, "-p", acc)
              case "-J" =>
                cmdSegmentsToSshConfigHostItems(ss, "-J", acc)
              case o if fullyCompacted.isDefined =>
                val compactedForSure = fullyCompacted.get
                cmdSegmentsToSshConfigHostItems(s"-${compactedForSure.substring(0, 1)}" :: compactedForSure.substring(1) :: ss, "", acc)
              case o if partlyCompacted.isDefined =>
                val partlyCompactedForSure = partlyCompacted.get
                cmdSegmentsToSshConfigHostItems(s"-${partlyCompactedForSure.substring(0, 1)}" :: ss, "", acc)
              case o if o.nonEmpty &&
                o.startsWith("-") && valueishSshOptions.contains(o.last) =>
                cmdSegmentsToSshConfigHostItems(ss, "skip", acc)
              case o if o.nonEmpty &&
                o.startsWith("-") && triggerishSshOptions.contains(o.last) =>
                cmdSegmentsToSshConfigHostItems(ss, "", acc)
              case hostSegment if indexOfLastAtSign > 0
                && indexOfLastAtSign < hostSegment.length - 1 =>
                val un = hostSegment.substring(0, indexOfLastAtSign)
                val hn = hostSegment.substring(indexOfLastAtSign + 1)
                cmdSegmentsToSshConfigHostItems(ss, "", acc.map(_.copy(username=Some(un) ,hostName=Some(hn))))
              case hostSegment if hostSegment.nonEmpty =>
                cmdSegmentsToSshConfigHostItems(ss, "", acc.map(_.copy(hostName=Some(hostSegment))))
              case _ =>
                Failure(new RuntimeException("shouldNotHappen"))
            }
          case _ =>
            Failure(new RuntimeException("shouldNotHappen, mark 2"))
        }
      case Nil =>
        acc.map{a=>
          if (a.name.toString.isEmpty && a.hostName.isDefined) {
            a.copy(name=SshConfigHeaderItem(a.hostName.get))
          } else a
        }
    }
  }
}

object ProxyCommandParser extends ProxyCommandParser