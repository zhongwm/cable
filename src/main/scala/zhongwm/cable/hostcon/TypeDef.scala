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

import zhongwm.cable.hostcon.Zssh.{jumpSessionL, sessionL}
import zhongwm.cable.hostcon.Zssh.types._
import zio.Runtime

object TypeDef {
  sealed trait TAction[+A]

  case class SshAction[+A](action: SshIO[A]) extends TAction[A]

  case class HostConnInfo(ho: String,
                          port: Int,
                          username: Option[String] = Some("root"),
                          password: Option[String],
                          privateKey: Option[KeyPair] = None,
                         )

  case class Nested[+Parent, +Child](a: Parent, b: Child)

  sealed abstract class ZSContext[A](val facts: Option[A], val data: Map[String, _], val parentLayer: Option[SessionLayer], val currentLayer: Option[SessionLayer])
  case class ZSSingleCtx[A](override val facts: Option[A], override val data: Map[String, _], override val parentLayer: Option[SessionLayer], override val currentLayer: Option[SessionLayer])
    extends ZSContext(
      facts, data, parentLayer, currentLayer)
  case class +|:[A, B](a: ZSContext[A], b: ZSContext[B])
    extends ZSContext[(A, B)](
      a.facts.flatMap{av=>b.facts.map{bv => (av, bv)}}, a.data ++ b.data, a.parentLayer, a.currentLayer)
  case class NestedC[A, B](a: ZSContext[A], b: ZSContext[B])
    extends ZSContext[Nested[A, B]](
      a.facts.flatMap{av=>b.facts.map{bv=>Nested(av, bv)}}, a.data ++ b.data, a.parentLayer, a.currentLayer)
  case class Unital(override val data: Map[String, _], override val parentLayer: Option[SessionLayer], override val currentLayer: Option[SessionLayer])
    extends ZSContext[Unit](
      Some(()), data, currentLayer, currentLayer)

  val nc1 = NestedC(ZSSingleCtx(Some(1), Map.empty, None, None), ZSSingleCtx(Some(""), Map.empty, None, None))


  sealed trait HostConnS[A] {
    type Repr  <: ZSContext[A]

    def run(ctx: ZSContext[A] = ZSSingleCtx(None, Map.empty, None, None)): Repr
  }

  protected[hostcon] def deriveSessionLayer(p: Option[SessionLayer], hc: HostConnInfo): SessionLayer = p match {
    case Some(l) =>
      /*println("===============")
      println("===bridging====")
      println("===============")*/
      jumpSessionL(l, hc.ho, hc.port, hc.username, hc.password, hc.privateKey)
    case None =>
      /*println("===============")
      println("===directco====")
      println("===============")*/
      sessionL(hc.ho, hc.port, hc.username, hc.password, hc.privateKey)
  }


  object HostConnS {

    final case class Parental[A, B](hc: HostConnS[A], nextLevel: HostConnS[B]) extends HostConnS[Nested[A, B]] {
      type Repr = NestedC[A, B]
      def run(ctx: ZSContext[Nested[A, B]]): NestedC[A, B] = {
        val newLayeredContext = this.hc.run(ZSSingleCtx(None, ctx.data, ctx.parentLayer, ctx.currentLayer))
        val childCtx = this.nextLevel.run(ZSSingleCtx(None, newLayeredContext.data, newLayeredContext.currentLayer, None))
        // val newFacts = newLayeredContext.facts.flatMap(av => childCtx.facts.map(bv => Nested(av, bv)))
        NestedC(newLayeredContext, childCtx)
      }
    }

    final case class +:[A, B](t: HostConnS[A], next: HostConnS[B]) extends HostConnS[(A, B)] {
      type Repr = +|:[A, B]

      override def run(ctx: ZSContext[(A, B)]): +|:[A, B] = {
        val hCtx = t.run(ZSSingleCtx(None, ctx.data, ctx.parentLayer, ctx.currentLayer))
        val tCtx = next.run(ZSSingleCtx(None, ctx.data, ctx.parentLayer, ctx.currentLayer))
        +|:(hCtx, tCtx)
      }
    }

    final case class JustConnect(hc: HostConnInfo) extends HostConnS[Unit] {
      type Repr = Unital

      override def run(ctx: ZSContext[Unit]): Unital = {
        val layer = deriveSessionLayer(ctx.parentLayer, hc)
        Unital(ctx.data, ctx.parentLayer, Some(layer))
      }
    }

    final case class Action[A](hc: HostConnInfo, action: TAction[A]) extends HostConnS[A] {
      type Repr = ZSContext[A]

      override def run(ctx: ZSContext[A]): ZSContext[A] = {
        action match {
          case SshAction(a) =>
            val layer = deriveSessionLayer(ctx.parentLayer, hc)
            val result = Runtime.default.unsafeRun(a.provideCustomLayer(layer))
            ZSSingleCtx(facts = Some(result), ctx.data, ctx.parentLayer, currentLayer = Some(layer))
        }
      }
    }

    sealed trait HCNil extends HostConnS[Unit] {
      type Repr = Unital
      // def +:[F[+_], B](t: HostConnS[F, B]) = t
      override def toString = "HCNil"
      override def run(ctx: ZSContext[Unit]): Unital = Unital(ctx.data, ctx.parentLayer, ctx.currentLayer)
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
