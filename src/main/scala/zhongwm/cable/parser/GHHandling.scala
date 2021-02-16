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

import zhongwm.cable.parser.Defs.{AGroup, AHost}
import zhongwm.cable.parser.HigherGroupHosts.envGroupHostAttrs
import cats._
import cats.implicits._

import scala.annotation.tailrec

object GHHandling {
  @tailrec
  def parentChain(ah: AHost, gs: List[AGroup] = Nil): List[AGroup] =
    ah.parent match {
      case None =>
        gs
      case Some(value) =>
        parentChain(ah, value :: gs)
    }

  def hostAttrs(ah: AHost): Map[String, Option[Any]] = {
    envGroupHostAttrs ++
      parentChain(ah).flatMap(_.attrs).toMap ++
      ah.attrs
  }

  def hostProxChains(ah: AHost): CF[List, HostConnectionTree] = {
    CF(HostConnectionTree("localhost", 22), List.empty[CF[List, HostConnectionTree]])
  }
}

case class RawHostConnectionInfo(
                           host: String,
                           port: Int,
                           user: String,
                           pwdId: String,
                           privKeyId: String,
                           bastionHost: String,
                           bastionPort: Int,
                           bastionUser: String,
                           bastionPwdId: String,
                           bastionPrivKeyId: String
                         )


case class HostConnectionTree(
  host: String,
  port: Int
                         ) {
}

case class Fx[F[_]](unfix: F[Fx[F]])
case class CF[F[_], A](head: A, tail: F[CF[F, A]])


