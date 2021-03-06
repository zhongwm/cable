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
import zhongwm.cable.hostcon.SshConn
import zhongwm.cable.hostcon.SshConn.types._
import zio._

object STC3 {

  trait HFunctor[F[_[+_], +_]] {
    def hmap[I[+_], J[+_]](nt: I ~> J): F[I, +*] ~> F[J, +*]
  }

  implicit val hostConnHFunctor: HFunctor[HostConn] = new HFunctor[HostConn] {
    override def hmap[I[+_], J[+_]](nt: I ~> J): HostConn[I, *] ~> HostConn[J, *] = new (HostConn[I, *] ~> HostConn[J, *]) {
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

  type HAlgebra[F[_[+_], +_], G[+_]] = F[G, +*] ~> G

  def hCata[F[_[+_], +_], G[+_], I](alg: HAlgebra[F, G], hFix: HFix[F, I])(implicit F: HFunctor[F]): G[I] = {
    val inner = hFix.unfix
    val nt = F.hmap(
      new (HFix[F, +*] ~> G) {
        override def apply[A](fa: HFix[F, A]): G[A] = hCata(alg, fa)
      }
    )(inner)
    alg(nt)
  }

  type Eval[+A] =  Unit

  // type HCE[A] = HFix[HostConnC, A]

  def hostConn2HostConnC[C, A](hc: HFix[HostConn, A], initialCtx: C, f: HostConnInfo[_] => C): HCFix[HostConnC, C, A] = {
    val unfix = hc.unfix

//    def hostConn2HostConnCInner[X](hc: List[HFix[HostConn, X]], parent: C): List[HCFix[HostConnC, C, X]] = { hc match {
//      case Nil =>
//        Nil
//      case x :: xs =>
//        hostConn2HostConnC(x, parent, f) :: hostConn2HostConnCInner(xs, parent)
//    }}
//    HCFix(HostConnC(initialCtx, unfix.hc, hostConn2HostConnCInner(unfix.nextLevel, f(unfix.hc))))
    HCFix(HostConnC(initialCtx, unfix.hc, unfix.nextLevel.foldLeft(List.empty[HCFix[HostConnC, C, _]]){(a, i) => hostConn2HostConnC(i, f(unfix.hc), f) :: a }))
  }

  def hostConn2HostConnCFg[F[_[+_], +_], G[_[+_], +_, +_], C, A](hc: HFix[F, A], parentCtx: C,
                                                            deriveChildContext: HFix[F, A] => C,
                                                            getList: HFix[F, A] => List[HFix[F, A]],
                                                            getA: HFix[F, A] => A,
                                                            ctor: (C, A, List[HCFix[G, C, A]]) => G[HCFix[G, C, +*], C, A]
                                                           ): HCFix[G, C, A] = {
    val unfix = hc.unfix

    def hostConn2HostConnCInner(hc: List[HFix[F, A]], parent: C): List[HCFix[G, C, A]] = { hc match {
      case Nil =>
        Nil
      case x :: xs =>
        hostConn2HostConnCFg(x, parent, deriveChildContext, getList, getA, ctor) :: hostConn2HostConnCInner(xs, parent)
    }}
    val asParentOfChildren = deriveChildContext(hc)
     HCFix(ctor(parentCtx, getA(hc), hostConn2HostConnCInner(getList(hc), asParentOfChildren)))
  }


  val exec: HAlgebra[HostConn, Eval] = new HAlgebra[HostConn, Eval] {
    override def apply[A](fa: HostConn[Eval, A]): Eval[A] = {
      fa.hc.action match {
        case ScriptAction(script) =>
          val value = script()
          println(value)
      }
    }
  }

  def toLayered(a: HCFix[HostConnC, Option[HostConnInfo[_]], _]): HCFix[HostConnC, Option[SessionLayer], _] = {
    val unfix = a.unfix
    def derive[A, B](parent: Option[HostConnInfo[A]], current: HostConnInfo[B]): SessionLayer = parent match {
      case None => SshConn.sessionL(new SshConn(Left(current.ho, current.port), current.userName, current.password, current.privKey))
      case Some(p) => SshConn.jumpSessionL(derive(None, p), current.ho, current.port, current.userName, current.password, current.privKey)
    }
    HCFix(HostConnC(Some(derive(unfix.parent, unfix.hc)), unfix.hc, unfix.nextLevel.foldLeft(List.empty[HCFix[HostConnC, Option[SessionLayer], _]]){(acc, i) => toLayered(i) :: acc}))
  }


//  val exec1: HAlgebra[HostConn, ]

  def main(args: Array[String]): Unit = {
    val initCtx: Option[HostConnInfo[_]] = None
    val value: HCFix[HostConnC, Option[HostConnInfo[_]], String] = hostConn2HostConnC(d2, initCtx, Some(_))
    println(value)
    println("-----")
    println(toLayered(value))
    println("=====")

    def getA[A](h: HostConn[Any, A]) = h.hc
//    def getList[F, A](h: HostConn[F, A]): List[HFix[HostConn, *]] = h.nextLevel
//    println(hostConn2HostConnCFg(d1, initCtx, {f => Some(getA(f.unfix))}, )
    hCata(exec, d2)
  }
}
