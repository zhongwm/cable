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

//package zhongwm.cable.hostcon
//
//import TypeDef._
//import zio.Runtime
//import Zssh._
//import Zssh.types._
//import zhongwm.cable.hostcon.syntax.Def.HFix

//object EagerExec {
//  import HostConnS._


  /*
//  def eager[F[+_], A](hs: F[A], ctx: ZSContext[_] = ZSContext((), None, None)): ZSContext[_] = {
  def eager[A](hs: HostConnS[A], ctx: ZSContext[A] = ZSContextI(None, Map.empty, None, None)): hs.Repr = {
    def currentLayer(p: Option[SessionLayer], hc: HostConnInfo): SessionLayer = p match {
      case Some(l) =>
        jumpSessionL(l, hc.ho, hc.port, hc.username, hc.password, hc.privateKey)
      case None =>
        sessionL(hc.ho, hc.port, hc.username, hc.password, hc.privateKey)
    }
    hs match {
      case Action(hc, action) =>
        action match {
          case SshAction(a) =>
            val layer = currentLayer(ctx.parentLayer, hc)
            val result = Runtime.default.unsafeRun(a.provideCustomLayer(layer))
            ctx.copy(facts = Some(result), currentLayer = Some(layer))
        }
      case JustConnect(hc) =>
        val layer = currentLayer(ctx.parentLayer, hc)
        ctx.copy(currentLayer = Some(layer))
      case Parental(p, s) =>
        val newLayer = eager(p, ctx)
        val childCtx = eager(s, newLayer.copy(data = newLayer.data, parentLayer = newLayer.currentLayer, currentLayer = None))
        val newFacts = Option(Nested(newLayer.facts.get, childCtx.facts.get))
        /*ctx.copy(facts = newFacts, data = newLayer.data ++ childCtx.data)*/
        NestedC(newLayer, childCtx)
      case +:(c, n) =>
        val hCtx = eager(c, ctx)
        val tCtx = eager(n, ctx.copy(data = hCtx.data))
        /*ctx.copy(facts = Some(hCtx.facts.get, tCtx.facts.get), hCtx.data ++ tCtx.data)*/
        `C+:`(hCtx, tCtx)
      case HCNil =>
        ctx
    }
  }

  */
//}
