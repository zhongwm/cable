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

package zhongwm.cable.zssh.hdfsyntax

import Hdf._
import cats.{Id, ~>}
import zhongwm.cable.zssh.{FactAction, SshAction, Zssh, ZsshContext}
import zhongwm.cable.zssh.Zssh.types._
import zhongwm.cable.zssh.internal.ZsshContextInternal
import zhongwm.cable.zssh.internal._
import zio._

import java.io.IOException

/**
 * Two Execution models
 * Two different execution models, eager and connection first.
 * In connection first mode, all connections are being established before doing anything, then actions get executed in a batch;
 * The other mode, eager, carries out the tasks promptly one by one.
 * Connection first mode
 */
object HdfSyntax {

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

  def hFold[F[_[+_], +_], G[+_], I](alg: HAlg[F, G], hFix: HFix[F, I])(implicit F: HFunctor[F]): G[I] = {
    val inner = hFix.unfix
    val nt = F(
      new (HFix[F, +*] ~> G) {
        override def apply[A](fa: HFix[F, A]): G[A] = hFold(alg, fa)
      }
    )(inner)
    alg(nt)
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

  def hostConn2HostConnC[C, A](hc: HFix[HostConn, A], initialCtx: C, f: HostConnInfo[_] => C): HCFix[HostConnC, C, A] = {
    val unfix = hc.unfix
    HCFix(HostConnC(initialCtx, unfix.hc, unfix.nextLevel.foldLeft(List.empty[HCFix[HostConnC, C, _]]){(a, i) => hostConn2HostConnC(i, f(unfix.hc), f) :: a }))
  }

  /**
   *
   * {{{
   * def getA[A](h: HostConn[Any, A]) = h.hc
   * def getList[F, A](h: HostConn[F, A]): List[HFix[HostConn, *]] = h.nextLevel
   * println(hostConn2HostConnCFg(d1, initCtx, {f => Some(getA(f.unfix))}, )
   * }}}
   */
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

  def execWithContext(initCtx: ZsshContext = ZsshContextInternal.empty): HCAlg[HostConnC, Exec, SessionLayer] = new HCAlg[HostConnC, Exec, SessionLayer] {
    var zsshContext: ZsshContext = initCtx

    override def apply[A](fa: HostConnC[Exec, SessionLayer, A]): Exec[A] = {
      fa.hc match {
        case HostAction(_, _, _, _, _, action) =>
          action match {
            case SshAction(sshio) =>
              Runtime.default.unsafeRun(sshio.flatMap {
                case result: SshScriptIOResult if result._1 != 0 => ZIO.fail(new IOException(s"Command failed with non-zero exit code.\n${result._2._2.mkString}"))
                case ar => ZIO.succeed(ar)
              }.map(a => (zsshContext, Some(a))).provideCustomLayer(fa.context ++ zsshContext.zsshContextL))
            case FactAction(name, sshio) =>
              Runtime.default.unsafeRun(sshio.flatMap {
                case result: SshScriptIOResult if result._1 != 0 => ZIO.fail(new IOException(s"Command failed with non-zero exit code.\n${result._2._2.mkString}"))
                case ar => ZIO.succeed(ar)
              }.tap(a => ZIO.effect{zsshContext = zsshContext.updated(name -> a)})
                .map(a => (zsshContext, Some(a))).provideCustomLayer(fa.context ++ zsshContext.zsshContextL))
          }
        case HostConnInfoNop(_, _, _, _, _) =>
          (zsshContext, None)
      }
    }
  }

  val inspection: HAlg[HostConn, Inspect] = new HAlg[HostConn, Inspect] {
    override def apply[A](fa: HostConn[Inspect, A]): Inspect[A] = {
      val current = fa.hc match {
        case HostAction(ho, port, _, _, _, action) =>
          action match {
            case SshAction(_) =>
              val rv = s"script in $ho:$port"
              println(rv)
              rv
            case FactAction(name, _) =>
              val rv = s"Named action $name in $ho:$port"
              println(rv)
              rv
          }
        case HostConnInfoNop(ho, port, _, _, _) =>
          val rv = s"Just connect $ho:$port"
          println(rv)
          rv
      }
      current ++ ":>" ++ fa.nextLevel.mkString(", ")
    }
  }

  def toLayered[A](a: HCFix[HostConnC, Option[HostConnInfo[_]], A]): HCFix[HostConnC, SessionLayer, A] = {
    val unfix = a.unfix
    def derive[T, U](parent: Option[HostConnInfo[T]], current: HostConnInfo[U]): SessionLayer = parent match {
      case None =>
        current match {
          case HostAction(ho, port, userName, password, privKey, _) =>
            Zssh.sessionL(Left(ho, port), userName, password, privKey)
          case HostConnInfoNop(ho, port, userName, password, privKey) =>
            Zssh.sessionL(Left(ho, port), userName, password, privKey)
        }
      case Some(p) =>
        current match {
          case HostAction(ho, port, userName, password, privKey, _) =>
            Zssh.jumpSessionL(derive(None, p), ho, port, userName, password, privKey)
          case HostConnInfoNop(ho, port, userName, password, privKey) =>
            Zssh.jumpSessionL(derive(None, p), ho, port, userName, password, privKey)
        }
    }
    HCFix(HostConnC(derive(unfix.context, unfix.hc), unfix.hc, unfix.nextLevel.foldLeft(List.empty[HCFix[HostConnC, SessionLayer, _]]){ (acc, i) => toLayered(i) :: acc}))
  }

  def executeSshIO[A](sshIO: HFix[HostConn, A]): Exec[A] = {
    val initCtx: Option[HostConnInfo[_]] = None
    val value: HCFix[HostConnC, Option[HostConnInfo[_]], A] = hostConn2HostConnC(sshIO, initCtx, Some(_))
    implicit val layered = toLayered(value)
    hcFold(execWithContext(ZsshContextInternal.empty), layered)
  }

}
