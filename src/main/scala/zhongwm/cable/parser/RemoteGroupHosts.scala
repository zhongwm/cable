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

/** Written by Wenming Zhong */

package zhongwm.cable.parser
import cats._
import cats.implicits._
import zhongwm.cable.parser.RemoteActionFileDefs.{AGroup, AHost}

import scala.annotation.tailrec
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object RemoteGroupHosts {
  val TOP_GROUP_NAME = "all"

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

  lazy val predefinedAHostAttrsUpper = predefinedAHostAttrs.map{x => x._1.toUpperCase -> x._2}

  lazy val envGroupHostAttrs:Map[String, Option[Any]] =
    predefinedAHostAttrs ++ System.getenv().asScala.toMap.map{kv => kv._1.toLowerCase -> kv._2}.
      filter{kv => predefinedAHostAttrs.keySet.exists(kv._1 === _)}.map{kv=>kv._1 -> (kv._1 match {
      case "ansible_port"=>
        Try{kv._2.toInt} match {
          case Failure(exception) =>
            22.some
          case Success(value) if 0 < value && value < 65536 =>
            value.some
          case _ =>
            22.some
        }
      case "ansible_become" =>
        kv._2.toLowerCase match {
          case "no" =>
            false.some
          case "yes" =>
            true.some
          case "false" =>
            false.some
          case "true" =>
            true.some
          case _ =>
            false.some
        }
      case _ =>
        kv._2.some
    })}

}
