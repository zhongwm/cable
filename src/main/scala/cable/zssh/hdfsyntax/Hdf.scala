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

package cable.zssh.hdfsyntax

import cable.zssh.TAction
import cable.zssh.Zssh.types._
import cats.~>
import cable.zssh.TypeDef.Host

import scala.concurrent.duration._

object Hdf {

  sealed trait DHostConn[+A]
  case class HostAction[+A](hostConnInfo: Host,
                            action: TAction[A]) extends DHostConn[A]

  case class HostNop[+A](hostConnInfo: Host
                                ) extends DHostConn[Nothing]

  case class HostConn[F[+_], +T](hc: DHostConn[T], nextLevel: List[F[_]])

  /**
   * A context can be an adaptive concept, it could either be a parent node
   * inside some meta(higher level) information or a context when running.
   */
  case class HostConnC[F[+_], +C, +T](context: C, hc: DHostConn[T], nextLevel: List[F[_]])

  case class HostConnMat[F[+_], +T](parentSessionL: Option[SessionLayer], sl: SessionLayer, hm: HostConnInfoMat[T], nextLevel: List[F[_]])

  case class HFix[F[_[+_], +_], +A](unfix: F[HFix[F, +*], A])
  case class HCFix[F[_[+_], +_, +_], C, +A](unfix: F[HCFix[F, C, +*], C, A])
  case class HCDFix[F[_[+_, +_], +_, +_], +C, +A](unfix: F[λ[(+[C], +[D]) => HCDFix[F, C, D]], C, A])

  def ssh[A](host: String, port: Option[Int]=None, username: Option[String]=None, password: Option[String]=None, privateKey: Option[KeyPair]=None, connectionTimeout: Option[Duration] = None, action: Option[TAction[A]]=None, children: Seq[HFix[HostConn, _]]=Nil): HFix[HostConn, A] = {
    action match {
      case None =>
        HFix(HostConn(HostNop(Host(host, port, username, password, privateKey, connectionTimeout)), children.toList))
      case Some(taction) =>
        HFix(HostConn(HostAction(Host(host, port, username, password, privateKey, connectionTimeout), taction), children.toList))
    }
  }

  type HAlg[F[_[+_], +_], G[+_]] = F[G, +*] ~> G

  trait HCFunctor[F[_[+_], _, +_], C] {
    def apply[I[+_], J[+_]](nt: I ~> J): F[I, C, +*] ~> F[J, C, +*]
  }

  trait HFunctor[F[_[+_], +_]] {
    def apply[I[+_], J[+_]](nt: I ~> J): F[I, +*] ~> F[J, +*]
  }

  type HCAlg[F[_[+_], +_, +_], G[+_], C] = F[G, C, +*] ~> G

  type Exec[+A] = List[Option[_]]

  type Inspect[+A] = String

}
