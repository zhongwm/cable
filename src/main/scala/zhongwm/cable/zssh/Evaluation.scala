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

package zhongwm.cable.zssh
import TypeDef._
import TypeDef.HostConnS._
import zio.Runtime


object Evaluation {

  trait HostConnSRun[X <: HostConnS[A], A] {
    def runI(ctx: ZSContext[A] = ZSSingleCtx(None, Map.empty, None, None)): X#Repr
  }

  implicit def actionHostConnSRun[A](x: Action[A]): HostConnSRun[Action[A], A] = new HostConnSRun[Action[A], A]{
    override def runI(ctx: ZSContext[A] = ZSSingleCtx(None, Map.empty, None, None)): ZSSingleCtx[A] = {
      x.action match {
        case SshAction(a) =>
          val layer = deriveSessionLayer(ctx.parentLayer, x.hc)
          val result = Runtime.default.unsafeRun(a.provideCustomLayer(layer))
          ZSSingleCtx(facts = Some(result), ctx.data, ctx.parentLayer, currentLayer = Some(layer))
        case FactAction(name, a) =>
          val layer = deriveSessionLayer(ctx.parentLayer, x.hc)
          val result = Runtime.default.unsafeRun(a.provideCustomLayer(layer))
          ZSSingleCtx(facts = Some(result), ctx.data + (name -> result), ctx.parentLayer, Some(layer))
      }
    }
  }

  implicit def parentalRun[A, B](x: Parental[A, B]): HostConnSRun[Parental[A, B], Nested[A, B]] = new HostConnSRun[Parental[A, B], Nested[A, B]] {
    override def runI(ctx: ZSContext[Nested[A, B]]): NestedC[A, B] = {
      val newLayeredContext = x.hc.run(ZSSingleCtx(None, ctx.data, ctx.parentLayer, ctx.currentLayer))
      val childCtx = x.nextLevel.run(ZSSingleCtx(None, newLayeredContext.data, newLayeredContext.currentLayer, None))
      // val newFacts = newLayeredContext.facts.flatMap(av => childCtx.facts.map(bv => Nested(av, bv)))
      NestedC(newLayeredContext, childCtx)
    }
  }

  implicit def consHostConnSRun[A, B](x: +:[A, B]): HostConnSRun[A +: B, (A, B)] = new HostConnSRun[A +: B, (A, B)] {
    override def runI(ctx: ZSContext[(A, B)]): A +|: B = {
      val hCtx = x.t.run(ZSSingleCtx(None, ctx.data, ctx.parentLayer, ctx.currentLayer))
      val tCtx = x.next.run(ZSSingleCtx(None, ctx.data, ctx.parentLayer, ctx.currentLayer))
      +|:(hCtx, tCtx)
    }
  }

  implicit def justConnectHostConnSRun(x: JustConnect): HostConnSRun[JustConnect, Unit] = new HostConnSRun[JustConnect, Unit] {
    override def runI(ctx: ZSContext[Unit]): Unital = {
      val layer = deriveSessionLayer(ctx.parentLayer, x.hc)
      Unital(ctx.data, ctx.parentLayer, Some(layer))
    }
  }

  implicit def hcnilConnectHostConnSRun(x: HCNil): HostConnSRun[HCNil, Unit] = new HostConnSRun[HCNil, Unit] {
    override def runI(ctx: ZSContext[Unit]): Unital = {
      Unital(ctx.data, ctx.parentLayer, ctx.currentLayer)
    }
  }


  /*implicit val action = Action("1", 2, "3", "4", scriptIO(""))
  implicit val nested1 = Parental(action, action)
  val a = action.runI()
  val n = nested1.runI()*/
}
