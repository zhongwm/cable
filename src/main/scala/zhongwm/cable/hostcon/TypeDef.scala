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

import zhongwm.cable.hostcon.Zssh.types._

object TypeDef {
  sealed trait TAction[+A]

  case class SshAction[+A](action: SshIO[A]) extends TAction[A]

  case class HostConnInfo(ho: String,
                          port: Int,
                          username: Option[String] = Some("root"),
                          password: Option[String],
                          privateKey: Option[KeyPair] = None,
                         )
  sealed trait HostConnS[+A]

  case class Nested[+Parent, +Child](a: Parent, b: Child)

  object HostConnS {

    final case class Parental[+A, +B](hc: HostConnS[A], nextLevel: HostConnS[B]) extends HostConnS[Nested[A, B]]

    final case class +:[+A, +B](t: HostConnS[A], next: HostConnS[B]) extends HostConnS[(A, B)]

    final case class JustConnect(hc: HostConnInfo) extends HostConnS[Nothing]

    final case class Action[+A](hc: HostConnInfo, action: TAction[A]) extends HostConnS[A]

    sealed trait HCNil extends HostConnS[Nothing] {
      // def +:[F[+_], B](t: HostConnS[F, B]) = t
      override def toString = "HCNil"
    }

    implicit class HostConnSOps[A](l: HostConnS[A]) {
      def +:[B](t: HostConnS[B]) = HostConnS.+:(t, l)
    }

    object HCNil extends HCNil

    def ssh[A, S <: HostConnS[A], B](host: String, port: Int, username: Option[String], password: Option[String], privateKey: Option[KeyPair], action: SshIO[A], children: HostConnS[B]) = {
      Parental(Action(HostConnInfo(host, port, username, password, privateKey), SshAction(action)), children)
    }

    def ssh[A, S <: HostConnS[A], B](host: String, port: Int, username: Option[String], password: Option[String], privateKey: Option[KeyPair], children: HostConnS[B]) = {
      Parental(JustConnect(HostConnInfo(host, port, username, password, privateKey)), children)
    }

  }

}
