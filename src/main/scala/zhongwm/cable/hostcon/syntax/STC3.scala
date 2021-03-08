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

package zhongwm.cable.hostcon.syntax

import STC._
import cats.{Id, ~>}
import zhongwm.cable.hostcon.Zssh
import zhongwm.cable.hostcon.Zssh.types._
import zio._

object STC3 {

  trait HFunctor[F[_[+_], +_]] {
    def apply[I[+_], J[+_]](nt: I ~> J): F[I, +*] ~> F[J, +*]
  }

  implicit val hostConnHFunctor: HFunctor[HostConn] = new HFunctor[HostConn] {
    override def apply[I[+_], J[+_]](nt: I ~> J): HostConn[I, *] ~> HostConn[J, *] = new (HostConn[I, *] ~> HostConn[J, *]) {
      override def apply[A](fa: HostConn[I, A]): HostConn[J, A] = {
        def pl[P[_], Q[_], X](xs: List[P[X]], ntt: P ~> Q): List[Q[X]] = {
          xs match {
            case Nil =>
              Nil
            case h :: t =>
              ntt(h) :: pl(t, ntt)
          }
        }
        HostConn(fa.hc, pl(fa.nextLevel, nt))
      }
    }
  }

  type HAlg[F[_[+_], +_], G[+_]] = F[G, +*] ~> G

  def hFold[F[_[+_], +_], G[+_], I](alg: HAlg[F, G], hFix: HFix[F, I])(implicit F: HFunctor[F]): G[I] = {
    val inner = hFix.unfix
    val nt = F(
      new (HFix[F, +*] ~> G) {
        override def apply[A](fa: HFix[F, A]): G[A] = hFold(alg, fa)
      }
    )(inner)
    alg(nt)
  }

  trait HCFunctor[F[_[+_], _, +_], C] {
    def apply[I[+_], J[+_]](nt: I ~> J): F[I, C, +*] ~> F[J, C, +*]
  }

  implicit def hostConnCHCFunctor[C, T](implicit hcc: HCFix[HostConnC, C, T]): HCFunctor[HostConnC, C] = new HCFunctor[HostConnC, C] {
    override def apply[I[+_], J[+_]](nt: I ~> J): HostConnC[I, C, +*] ~> HostConnC[J, C, +*] =
      new (HostConnC[I, C, +*] ~> HostConnC[J, C, +*]) {
        override def apply[A](fa: HostConnC[I, C, A]): HostConnC[J, C, A] = {
          HostConnC(fa.context, fa.hc, fa.nextLevel.foldLeft(List.empty[J[_]]){ (acc, i) => nt(i) :: acc})
        }
      }
  }

  def hcFold[F[_[+_], +_, +_], G[+_], C, I](alg: HCAlg[F, G, C], hcFix: HCFix[F, C, I])(implicit F: HCFunctor[F, C]): G[I] = {
    val inner = hcFix.unfix
    val nt = F(
      new (HCFix[F, C, +*] ~> G) {
        override def apply[A](fa: HCFix[F, C, A]): G[A] = hcFold(alg, fa)
      }
    )(inner)
    alg(nt)
  }

  type HCAlg[F[_[+_], +_, +_], G[+_], C] = F[G, C, +*] ~> G

  type Exec[+A] = Either[Any, A]

  type Inspect[+A] = Unit

  def hostConn2HostConnC[C, A](hc: HFix[HostConn, A], initialCtx: C, f: HostConnInfo[_] => C): HCFix[HostConnC, C, A] = {
    val unfix = hc.unfix
    HCFix(HostConnC(initialCtx, unfix.hc, unfix.nextLevel.foldLeft(List.empty[HCFix[HostConnC, C, _]]){(a, i) => hostConn2HostConnC(i, f(unfix.hc), f) :: a }))
  }

  def hostConn2HostConnCFg[F[_[+_], +_], G[_[+_], +_, +_], C, A](hc: HFix[F, A], parentCtx: C,
                                                            deriveChildContext: HFix[F, A] => C,
                                                            getList: HFix[F, A] => List[HFix[F, A]],
                                                            getA: HFix[F, A] => A,
                                                            ctor: (C, A, List[HCFix[G, C, A]]) => G[HCFix[G, C, +*], C, A]
                                                           ): HCFix[G, C, A] = {
    def hostConn2HostConnCInner(hc: List[HFix[F, A]], parent: C): List[HCFix[G, C, A]] = hc match {
      case Nil =>
        Nil
      case x :: xs =>
        hostConn2HostConnCFg(x, parent, deriveChildContext, getList, getA, ctor) :: hostConn2HostConnCInner(xs, parent)
    }
    val asParentOfChildren = deriveChildContext(hc)
    HCFix(ctor(parentCtx, getA(hc), hostConn2HostConnCInner(getList(hc), asParentOfChildren)))
  }

  val execWithContext: HCAlg[HostConnC, Exec, Option[SessionLayer]] = new HCAlg[HostConnC, Exec, Option[SessionLayer]] {
    override def apply[A](fa: HostConnC[Exec, Option[SessionLayer], A]): Exec[A] = {
      fa.hc.action match {
        case ScriptAction(script) =>
          fa.context.map{ pp => Runtime.default.unsafeRun(script.provideCustomLayer(pp))}.fold(Left("no value"): Either[String, A])(v => Right[String, A](v): Either[String, A])
      }
    }
  }

  val inspection: HAlg[HostConn, Inspect] = new HAlg[HostConn, Inspect] {
    override def apply[A](fa: HostConn[Inspect, A]): Inspect[A] = {
      fa.hc.action match {
        case ScriptAction(script) =>
          println(script)
      }
    }
  }

  def toLayered[A](a: HCFix[HostConnC, Option[HostConnInfo[_]], A]): HCFix[HostConnC, Option[SessionLayer], A] = {
    val unfix = a.unfix
    def derive[T, U](parent: Option[HostConnInfo[T]], current: HostConnInfo[U]): SessionLayer = parent match {
      case None => Zssh.sessionL(Left(current.ho, current.port), current.userName, current.password, current.privKey)
      case Some(p) => Zssh.jumpSessionL(derive(None, p), current.ho, current.port, current.userName, current.password, current.privKey)
    }
    HCFix(HostConnC(Some(derive(unfix.context, unfix.hc)), unfix.hc, unfix.nextLevel.foldLeft(List.empty[HCFix[HostConnC, Option[SessionLayer], _]]){ (acc, i) => toLayered(i) :: acc}))
  }

  def executeSshIO[A](sshIO: HFix[HostConn, A]): Exec[A] = {
    val initCtx: Option[HostConnInfo[_]] = None
    val value: HCFix[HostConnC, Option[HostConnInfo[_]], A] = hostConn2HostConnC(sshIO, initCtx, Some(_))
    implicit val layered = toLayered(value)
    hcFold(execWithContext, layered)
  }

}
