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

import zhongwm.cable.parser.SshConfigParser.{configRegistry, defaultSshUser, defaultUserSshKeyPair}
import zhongwm.cable.zssh.Zssh.{jumpSessionL, sessionL}
import zhongwm.cable.zssh.Zssh.types._
import zhongwm.cable.zssh.internal.ZsshContextInternal
import cats.implicits._
import scala.util._
import zhongwm.cable.parser.{HostItem, ProxyHostByName, ProxySetting, SshConfigParser}
import zio.Runtime

object TypeDef {

  case class Host(host: String,
                  port: Option[Int] = None,
                  username: Option[String] = None,
                  password: Option[String] = None,
                  privateKey: Option[KeyPair] = None,
                  )

  case class Nested[+Parent, +Child](parent: Parent, child: Child)

  sealed abstract class ZSContext[A](val result: Option[A], val data: Map[String, _], val parentLayer: Option[SessionLayer], val currentLayer: Option[SessionLayer])

  case class ZSSingleCtx[A](override val result: Option[A], override val data: Map[String, _], override val parentLayer: Option[SessionLayer], override val currentLayer: Option[SessionLayer])
    extends ZSContext(
      result, data, parentLayer, currentLayer)

  case class +|:[A, B](a: ZSContext[A], b: ZSContext[B])
    extends ZSContext[(A, B)](
      a.result.flatMap{ av=>b.result.map{ bv => (av, bv)}}, a.data ++ b.data, a.parentLayer, a.currentLayer)

  /**
   * {{{val nc1 = NestedC(ZSSingleCtx(Some(1), Map.empty, None, None), ZSSingleCtx(Some(""), Map.empty, None, None))}}}
   */
  case class NestedC[A, B](parent: ZSContext[A], child: ZSContext[B])
    extends ZSContext[Nested[A, B]](
      parent.result.flatMap{ av=>child.result.map{ bv=>Nested(av, bv)}}, parent.data ++ child.data, parent.parentLayer, parent.currentLayer)

  case class Unital(override val data: Map[String, _], override val parentLayer: Option[SessionLayer], override val currentLayer: Option[SessionLayer])
    extends ZSContext[Unit](
      Some(()), data, currentLayer, currentLayer)


  sealed trait HostConnS[A] {
    type Repr  <: ZSContext[A]

    def run(ctx: ZSContext[A] = ZSSingleCtx(None, Map.empty, None, None)): Repr
  }

  private[cable] def deriveParentSessionFromSshConfig(proxySetting: ProxySetting): Option[SessionLayer] = {
    proxySetting match {
      case ProxyHostByName(name) =>
        configRegistry.flatMap(_.hostItems.get(name)).flatMap(_.proxyJumper).flatMap(deriveParentSessionFromSshConfig)
      case HostItem(name, hostName, port, username, password, privateKey, proxyJumper) =>
        proxyJumper.flatMap {
          case ps@ProxyHostByName(_) =>
            deriveParentSessionFromSshConfig(ps)
          case _ =>
            // Not gonna happen as we know for sure from the form of the ProxyCommand, and we've parsed it.
            None
        }.fold {
          (hostName, port.orElse(Some(22))).mapN((h, p) => sessionL(Left(h, p), username, password, privateKey))
        } { pl =>
          (hostName, port.orElse(Some(22))).mapN((h, p) => jumpSessionL(pl, h, p, username, password, privateKey))
        }
    }
  }

  private[cable] def getConfiguredProxySetting(hostName: String): Option[ProxySetting] = {
    configRegistry.flatMap(_.hostItems.get(hostName)).flatMap(_.proxyJumper)
  }

  def layerForHostConnInfo(hc: Host): SessionLayer =
    getConfiguredProxySetting(hc.host).flatMap(deriveParentSessionFromSshConfig).fold {
      val defaultedHost = respectSshConfig(hc)
      sessionL(defaultedHost.host, defaultedHost.port.getOrElse(22), defaultedHost.username, defaultedHost.password, defaultedHost.privateKey)
    } { pl =>
      val defaultedHost = respectSshConfig(hc)
      jumpSessionL(pl, defaultedHost.host, defaultedHost.port.getOrElse(22), defaultedHost.username, defaultedHost.password, defaultedHost.privateKey)
    }

  protected[zssh] def deriveSessionLayer(p: Option[SessionLayer], hc: Host): SessionLayer =
    p match {
      case Some(l) =>
        val defaultedHost = respectSshConfig(hc)
        jumpSessionL(l, defaultedHost.host, defaultedHost.port.getOrElse(22), defaultedHost.username, defaultedHost.password, defaultedHost.privateKey)
      case None =>
        layerForHostConnInfo(hc)
    }

  /**
   * Default values from user home ssh config.
   *
   * Explicitly specified attributes take precedence.
   * When defaulting password, if a private key is given, then don't default it.
   * @param hostConn
   * @return
   */
  def respectSshConfig(hostConn: Host): Host = {
    val configItem = configRegistry.flatMap(_.hostItems.get(hostConn.host))
    val hostName: String = configItem.flatMap(_.hostName).getOrElse(hostConn.host)
    val port: Int = hostConn.port.orElse(configItem.flatMap(_.port)).getOrElse(22)
    val userName: String = hostConn.username.orElse(configItem.flatMap(_.username)).getOrElse(defaultSshUser)
    // private key comes over password in priority, so we first check if a privateKey is specified.
    // if privateKey is not given from api, default to ssh config.
    val password: Option[String] = hostConn.password.orElse(hostConn.privateKey.fold(configItem.flatMap(_.password))(_=>None))
    val privateKey: Option[KeyPair] = hostConn.privateKey.orElse(configItem.flatMap(_.privateKey)).orElse(defaultUserSshKeyPair.toOption)
    hostConn.copy(host=hostName, port=Some(port), username=Some(userName), password=password, privateKey=privateKey)
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
        val tCtx = next.run(ZSSingleCtx(None, hCtx.data, ctx.parentLayer, ctx.currentLayer))
        +|:(hCtx, tCtx)
      }
    }

    final case class JustConnect(hc: Host) extends HostConnS[Unit] {
      type Repr = Unital

      override def run(ctx: ZSContext[Unit]): Unital = {
        val layer = deriveSessionLayer(ctx.parentLayer, hc)
        Unital(ctx.data, ctx.parentLayer, Some(layer))
      }
    }

    object JustConnect {
      def apply(ho: String, port: Option[Int] = None, username: Option[String] = None, password: Option[String] = None, privateKey: Option[KeyPair] = None): JustConnect =
        JustConnect(Host(ho, port, username, password, privateKey))
    }


    final case class Action[A](hc: Host, action: TAction[A]) extends HostConnS[A] {
      type Repr = ZSContext[A]

      override def run(ctx: ZSContext[A]): ZSContext[A] = {
        action match {
          case SshAction(a) =>
            val layer = deriveSessionLayer(ctx.parentLayer, hc)
            val result = Runtime.default.unsafeRun(a.provideCustomLayer(layer ++ ZsshContextInternal.lFromData(ctx.data)))
            ZSSingleCtx(result = Some(result), ctx.data, ctx.parentLayer, currentLayer = Some(layer))
          case FactAction(name, a) =>
            val layer = deriveSessionLayer(ctx.parentLayer, hc)
            val result = Runtime.default.unsafeRun(a.provideCustomLayer(layer ++ ZsshContextInternal.lFromData(ctx.data)))
            ZSSingleCtx(result = Some(result), ctx.data + (name -> result), ctx.parentLayer, Some(layer))
        }
      }
    }

    object Action {
      def apply[A](ho: String, port: Option[Int] = None, username: Option[String] = None, password: Option[String] = None, privateKey: Option[KeyPair] = None, action: SshIO[A]): Action[A] =
        Action(Host(ho, port, username, password, privateKey), SshAction(action))
      def asFact[A](ho: String, port: Option[Int] = None, username: Option[String] = None, password: Option[String] = None, privateKey: Option[KeyPair] = None, factName: String, action: SshIO[A]): Action[A] =
        Action(Host(ho, port, username, password, privateKey), FactAction(factName, action))
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

    def ssh[A, S <: HostConnS[A], B](host: String, port: Option[Int], username: Option[String], password: Option[String], privateKey: Option[KeyPair], action: SshIO[A], children: HostConnS[B]) = {
      Parental(Action(Host(host, port, username, password, privateKey), SshAction(action)), children)
    }

    def ssh[A, S <: HostConnS[A], B](host: String, port: Option[Int], username: Option[String], password: Option[String], privateKey: Option[KeyPair], factName: String, action: SshIO[A], children: HostConnS[B]) = {
      Parental(Action(Host(host, port, username, password, privateKey), FactAction(factName, action)), children)
    }

    def ssh[A, S <: HostConnS[A], B](host: String, port: Option[Int], username: Option[String], password: Option[String], privateKey: Option[KeyPair], children: HostConnS[B]) = {
      Parental(JustConnect(Host(host, port, username, password, privateKey)), children)
    }

  }

}
