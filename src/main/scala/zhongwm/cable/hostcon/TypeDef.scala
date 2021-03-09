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
 */

/* Written by Wenming Zhong */

package zhongwm.cable.hostcon

import Zssh.types._

object TypeDef {
  sealed trait SshAction[+A]

  case class ScriptAction[+A](action: SshIO[A]) extends SshAction[A]

  case class HostConnInfo(ho: String,
                              port: Int,
                              username: Option[String] = Some("root"),
                              password: Option[String],
                              privateKey: Option[KeyPair],
                              )
  sealed trait HostConnS

  object HostConnS {

    final case class Parental[+T <: HostConnS, +U <: HostConnS](hc: T, nextLevel: U) extends HostConnS

    final case class +:[+T <: HostConnS, +U <: HostConnS](t: T, next: U) extends HostConnS

    final case class JustConnect(hc: HostConnInfo) extends HostConnS

    final case class Action[+T](hc: HostConnInfo, action: SshAction[T]) extends HostConnS

    sealed trait HCNil extends HostConnS {
      override def toString = "HCNil"
    }

    implicit class HostConnSOps[L <: HostConnS](l: L) {
      def +:[T <: HostConnS](t: T) = HostConnS.+:(t, l)
    }

    object HCNil extends HCNil

    def ssh[A, S <: HostConnS](host: String, port: Int, username: Option[String], password: Option[String], privateKey: Option[KeyPair], action: SshIO[A], children: HostConnS) = {
      Parental(Action(HostConnInfo(host, port, username, password, privateKey), ScriptAction(action)), children)
    }

    def ssh[A, S <: HostConnS](host: String, port: Int, username: Option[String], password: Option[String], privateKey: Option[KeyPair], children: HostConnS) = {
      Parental(JustConnect(HostConnInfo(host, port, username, password, privateKey)), children)
    }

  }
  
}
