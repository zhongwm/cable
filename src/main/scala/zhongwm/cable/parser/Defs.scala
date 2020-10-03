/*
 * Copyright 2020, Wenming Zhong
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
 *
 * by Zhongwenming<br>
 */

package zhongwm.cable.parser

import java.io.File

import org.ini4j.{Ini, Wini}
import zio._
import console._
import atto._
import Atto._
import atto.ParseResult._
import cats._
import cats.implicits._

import scala.annotation.tailrec

object Defs {
  case class AInventory()

  case class AGroup(groupName: String, items: Map[String, AHost]=Map.empty, attrs: Map[String, Option[Any]]=Map.empty, parent: Option[AGroup]=None, children: List[AGroup]=Nil)

  case class AHost(name: String, attrs: Map[String, Option[Any]])
  
  val predefinedAHostAttrs: Map[String, Option[Any]] = Map(
    "ansible_connection" -> None,
    "ansible_host" -> None,
    "ansible_port" -> Some(22),
    "ansible_user" -> Some("root"),
    "ansible_password" -> None,
    "ansible_ssh_private_key_file" -> None,
    "ansible_ssh_common_args" -> None,
    "ansible_sftp_extra_args" -> None,
    "ansible_scp_extra_args" -> None,
    "ansible_ssh_extra_args" -> None,
    "ansible_ssh_pipelining" -> None,
    "ansible_ssh_executable" -> Some("/usr/bin/ssh"),
    "ansible_become" -> Some(true),
    "ansible_become_method" -> Some("su"),
    "ansible_become_user" -> Some("root"),
    "ansible_become_password" -> None,
    "ansible_become_exe" -> Some("sudo"),
    "ansible_become_flags" -> Some("-HSn"),
    "ansible_shell_type" -> None,
    "ansible_python_interpreter" -> Some("/usr/bin/python"),
    "ansible_ruby_interpreter" -> Some("/usr/bin/ruby"),
    "ansible_perl_interpreter" -> Some("/usr/bin/perl"),
    "ansible_shell_executable" -> Some("bin/sh"),
  )

  sealed trait PResult
  case class PRGroup(name: String, attrs: Map[String, Option[Any]]=Map.empty, items:Map[String, PRHostDef]=Map.empty) extends PResult
  case class PRHostDef(name: String, attrs: Map[String, Option[Any]]=Map.empty) extends PResult
//  case class PRGroupVars(group: PRGroup) extends PResult

  case class ParseFailure[A](msg: String, maybeParseFail: Option[Fail[A]])// extends PResult

  /**
   * Ini style inventory file, flat groups.
   */
  object IniLex {
    def isAGroupLine(l: String): (String, Boolean) = (char('[') ~> many(noneOf("[]")) <~ char(']') ).parseOnly(l) match {
        case Done(r, cl) =>
          (cl.mkString, true)
        case _ =>
          ("", false)
      }

    def isEmptyLine(l: String): Boolean = {
      l.isEmpty
    }

    @tailrec
    def parseGroup(acc: String, seq: String): (String, String) = {
      anyChar.parseOnly(seq) match {
        case Fail(in, _, msg) =>
          (acc, "")
        case Done(r, '[') =>
          parseGroup(acc, r)
        case Done(r, ']') =>
          (acc, "")
        case Done(r, c) if c != ']' =>
          parseGroup(acc + c, r)
      }
    }

    /**
     * ||
     * v
     * @param groupName
     * @return groupName -> isGroup(boolean)
     */
    def isGroupVarSec(groupName: String): (String, Boolean) = {
      if (groupName.contains(":vars")) {
        (groupName.substring(1, groupName.lastIndexOf(':')), true)
      } else {
        ("",  false)
      }
    }

    /**
     * ||
     * v
     * @param l
     * @return
     */
    def parseGroupVarDef(l: String): Option[(String, Option[String])] = {
      many(notChar('=')).parse(l) match {
        case Partial(k) =>
          None
        case Done(r, cl) =>
          val value = (char('=') ~> many(anyChar)).parseOnly(r) match {
            case Fail(_,_,_) =>
              ""
            case Done(rr, cll) =>
              cll.mkString
          }
          Some(cl.mkString -> value.some)
      }
    }

    @tailrec
    def parseGroupVarDefs(ll: List[String], pd:List[(String, Option[String])]=Nil): (List[String], List[(String, Option[String])]) = {
      ll match {
        case Nil =>
          (Nil, pd)
        case l :: ls if isAGroupLine(l)._2 =>
          (ls, pd)
        case l :: ls if !isAGroupLine(l)._2 =>
          val tryParsing = parseGroupVarDef(l).map{List(_)}
          parseGroupVarDefs(ls,  pd.some.combine(tryParsing).getOrElse(Nil))
      }
    }


    object HostLineP {
      @tailrec
      def parseVarDef[A](l: String, mm: Map[String, Option[String]]=Map.empty): Either[ParseFailure[A], (String, Map[String, Option[String]])] = {
        // println(l)
        val sl = many1(oneOf(" \t")).parseOnly(l) match {
          case Done(input, result) =>
            input
          case Fail(_, _, _) =>
            l
        }
        val (lr, k) = many1(noneOf("[]=")) <~ char('=') parseOnly sl match {
          case Done(input, result) =>
            (input, result.mkString_("").some)
          case Fail(input, stack, msg) =>
            (input, None)
        }
        if (k.isEmpty) {
          // no key, v is not a failure, need more look
          if (!sl.isEmpty) Left(ParseFailure(s"malformed vars def: $sl", None))
          else {
            // println("\t" + k)
            Right(lr, mm)
          }
        } else {
          val openSingleQuote = lr(0) === '\''
          val openDoubleQuote = lr(0) === '\"'
          val lr1 = if (openSingleQuote || openDoubleQuote) {
            lr.substring(1)
          } else {
            lr
          }

          @tailrec
          def parseAval(in: String, vv: List[Char]=Nil): (String, List[Char]) = anyChar parseOnly in match {
            case Fail(input, stack, message) =>
              (input, vv)  // end
            case Done(input, result) =>
              if (result === '\'')
                if (vv.head == '\\')
                  parseAval(input, result :: vv)
                else
                  if (openSingleQuote)
                    (input, vv)
                  else
                    parseAval(input, result:: vv)
              else if (result == '\"')
                if (vv.head == '\\')
                  parseAval(input, result :: vv)
                else
                  if (openDoubleQuote)
                    (input, vv)
                  else
                    parseAval(input, result::vv)
              else if (result == ' ') {
                if (!openSingleQuote && !openDoubleQuote) {
                  (input, vv)
                } else {
                  parseAval(input, result::vv)
                }
              }else
                parseAval(input, result::vv)
          }

          val (input, v) = parseAval(lr1)
          val mm1 = mm + (k.get -> v.reverse.mkString.some)
          parseVarDef(input, mm1)
        }
      }

      def parseHostLine[_](group: PRGroup, l: String): Either[ParseFailure[_], PRHostDef] =
        notChar('[') ~ many1(noneOf("[]= ")) parseOnly l match {
          case fail@Fail(input, stack, message) =>
            Left(ParseFailure(s"Not valid host line: $l", fail.some))
          case Done(input, result) =>
            val pv = parseVarDef(input)
            pv.fold({l=>
              val m: Map[String, Int] = Map.empty
              Left(l)
            }, { b =>
              Right(PRHostDef((result._1 :: result._2).mkString_(""), b._2))
            })
        }
    }

    /**
     * parse in total
     * @param gs
     * @param ll
     * @return
     */
    @tailrec
    def parse(gs: List[PRGroup], ll: List[String]): (List[PRGroup], List[String]) = {/*println(gs); */ ll match {
      case l :: ls if isEmptyLine(l) =>
        parse(gs, ls)
      case l :: ls if isAGroupLine(l)._2 =>
        val isGroupVarRet = isGroupVarSec(l)
        // println(isGroupVarRet) // debug
        if (isGroupVarRet._2) {
          val groupName = isGroupVarRet._1
          var existing = gs.find(_.name === groupName)
          if (existing.isDefined) {
            val parseGroupVarDefsResult = parseGroupVarDefs(ls)
            // println(parseGroupVarDefsResult)  // debug
            existing = existing.map{e=>e.copy(attrs=e.attrs ++ parseGroupVarDefsResult._2)}
            parse(existing.get :: gs.filterNot{p=>existing.get.name === p.name}, parseGroupVarDefsResult._1)
          } else {
            parse(PRGroup(groupName) :: gs, ll)
          }
        } else {
          parse(PRGroup(parseGroup("", l)._1) :: gs,  ls)
        }
      case l :: ls if !isAGroupLine(l)._2 =>
        // use head as group
        val ph = HostLineP.parseHostLine(gs.head, l)
        if (ph.isLeft) {
          println(ph.left.getOrElse("Not a host"))
          parse(gs, ls)
        } else {
          val phv = ph.toOption.get
          parse(gs.head.copy(items=(gs.head.items + (phv.name -> phv))) :: gs.tail, ls)
        }
      case Nil =>
        (gs, Nil)
      case _ =>
        (gs, Nil)
    }}

    def parseMerged(ll: List[String]): AGroup = {
      val (groups, rl) = parse(List.empty, ll)
      // println("=====after parser====")

      // println(groups)
      var subGroups = groups.map{ pg =>
        val aHosts = pg.items.map{ph => ph._1 -> AHost(ph._1, ph._2.attrs)}
        AGroup(pg.name, items=aHosts, attrs=pg.attrs)
      }
      var topGroupOpt = subGroups.find(_.groupName == "global")
      var topGroup = if (topGroupOpt.isDefined) topGroupOpt.get else AGroup("global")
      if (topGroupOpt.isEmpty) {
        subGroups = topGroup :: subGroups
      }
      topGroup = topGroup.copy(children=subGroups.filterNot{sg => sg.groupName === topGroup.groupName})
      // todo ¿¿¿
      topGroup
    }
  }

  def ansibleLines(lines: Iterable[String], parentName: Option[String]): List[PResult] = {
    List()
  }

  def readInventoryFile(filePath: String): Task[Option[AGroup]] = ZIO.effect {
    val ini: Ini = new Ini(new File(filePath))

    predefinedAHostAttrs.iterator.toList
    ini.forEach{(s, ss) => {
      AHost(
        "",
        Map(predefinedAHostAttrs.toList:_*)
      )
    }}
    None
  }

  def main(args: Array[String]): Unit = {
    println("ok")
  }

}
