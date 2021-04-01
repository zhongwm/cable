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
import zhongwm.cable.zssh.TypeDef.{HostConnInfo, layerForHostConnInfo}
import zhongwm.cable.zssh.TypeDef.HostConnS.respectSshConfig
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

  def hostConn2HostConnC[C, A](hc: HFix[HostConn, A], initialCtx: C, f: DHostConn[_] => C): HCFix[HostConnC, C, A] = {
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

  def execWithContext(initCtx: ZsshContext = ZsshContextInternal.empty): HCAlg[HostConnC, Exec, (ZsshContext, Option[_], SessionLayer)] = new HCAlg[HostConnC, Exec, (ZsshContext, Option[_], SessionLayer)] {
    var zsshContext: ZsshContext = initCtx

    override def apply[A](fa: HostConnC[Exec, (ZsshContext, Option[_], SessionLayer), A]): Exec[A] = {
      fa.context._2 :: fa.nextLevel.flatten
    }
  }

  val inspection: HAlg[HostConn, Inspect] = new HAlg[HostConn, Inspect] {
    override def apply[A](fa: HostConn[Inspect, A]): Inspect[A] = {
      val current = fa.hc match {
        case HostAction(HostConnInfo(ho, port, _, _, _), action) =>
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
        case HostConnInfoNop(HostConnInfo(ho, port, _, _, _)) =>
          val rv = s"Just connect $ho:$port"
          println(rv)
          rv
      }
      current ++ ":>" ++ fa.nextLevel.mkString(", ")
    }
  }

  def toLayered[A](a: HCFix[HostConnC, Option[DHostConn[_]], A], zsshContext: ZsshContext = ZsshContextInternal.empty): HCFix[HostConnC, (ZsshContext, Option[_], SessionLayer), A] = {
    val unfix = a.unfix
    def derive[T, U](parent: Option[DHostConn[T]], current: DHostConn[U]): SessionLayer = parent match {
      case None =>
        current match {
          case HostAction(hci @ HostConnInfo(_, _, _, _, _), _) =>
            val defaulted = respectSshConfig(hci)
            layerForHostConnInfo(defaulted)
          case HostConnInfoNop(hci @ HostConnInfo(_, _, _, _, _)) =>
            val defaulted = respectSshConfig(hci)
            layerForHostConnInfo(defaulted)
        }
      case Some(p) =>
        current match {
          case HostAction(hci @ HostConnInfo(_, _, _, _, _), _) =>
            val defaulted = respectSshConfig(hci)
            Zssh.jumpSessionL(derive(None, p), defaulted.ho, defaulted.port.get, defaulted.username, defaulted.password, defaulted.privateKey)
          case HostConnInfoNop(hci @ HostConnInfo(_, _, _, _, _)) =>
            val defaulted = respectSshConfig(hci)
            Zssh.jumpSessionL(derive(None, p), defaulted.ho, defaulted.port.get, defaulted.username, defaulted.password, defaulted.privateKey)
        }
    }

    val cl = derive(unfix.context, unfix.hc)
    val vr = evalActionWith(unfix.hc, zsshContext, cl)
    HCFix(HostConnC(vr, unfix.hc,
      unfix.nextLevel.foldRight(
        (vr._1, List.empty[HCFix[HostConnC, (ZsshContext, Option[_], SessionLayer), _]])){ (i, acc) =>
        val value: HCFix[HostConnC, (ZsshContext, Option[_], SessionLayer), _] = toLayered(i, acc._1)
        (value.unfix.context._1, value :: acc._2)
      }._2
    ))
  }

  def evalActionWith[A](current: DHostConn[A], inZsshContext: ZsshContext, sl: SessionLayer) = {
    current match {
      case HostAction(hci @ HostConnInfo(_, _, _, _, _), ha) =>
        val defaulted = respectSshConfig(hci)
        val sl = layerForHostConnInfo(defaulted)
        ha match {
          case SshAction(sshio) =>
            val ur = Runtime.default.unsafeRun(sshio.flatMap {
              case result: SshScriptIOResult if result.exitCode != 0 => ZIO.fail(new IOException(s"Command failed with non-zero exit code.\n${result.stderr.mkString}"))
              case ar => ZIO.succeed(ar)
            }.provideCustomLayer(sl ++ inZsshContext.zsshContextL))
            (inZsshContext, Some(ur), sl)
          case FactAction(name, sshio) =>
            val ur = Runtime.default.unsafeRun(sshio.flatMap {
              case result: SshScriptIOResult if result.exitCode != 0 => ZIO.fail(new IOException(s"Command failed with non-zero exit code.\n${result.stderr.mkString}"))
              case ar => ZIO.succeed(ar)
            }.map(a => (inZsshContext.updated(name -> a), Some(a))).provideCustomLayer(sl ++ inZsshContext.zsshContextL))
            (ur._1, ur._2, sl)
        }
      case HostConnInfoNop(hci @ HostConnInfo(_, _, _, _, _)) =>
        val defaulted = respectSshConfig(hci)
        val sl = layerForHostConnInfo(defaulted)
        (inZsshContext, None, sl)
    }
  }

  def executeSshIO[A](sshIO: HFix[HostConn, A]): Exec[A] = {
    val initCtx: Option[DHostConn[_]] = None
    val value: HCFix[HostConnC, Option[DHostConn[_]], A] = hostConn2HostConnC(sshIO, initCtx, Some(_))
    implicit val layered: HCFix[HostConnC, (ZsshContext, Option[_], SessionLayer), A] = toLayered(value)
    hcFold(execWithContext(ZsshContextInternal.empty), layered)
  }

}
