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

import zhongwm.cable.hostcon.SshConn
import zhongwm.cable.hostcon.SshConn.types._

object STC {

  sealed trait ScaAnsible[+A]
  // case class ScriptAction[+A](action: () => A) extends ScaAnsible[A]
  case class ScriptAction[+A](action: SshIO[A]) extends ScaAnsible[A]

  case class HostConnInfo[+A](ho: String,
                             port: Int,
                             userName: Option[String] = Some("root"),
                             password: Option[String],
                             privKey: Option[KeyPair],
                             action: ScaAnsible[A])
  case class HostConn[F[+_], +T](hc: HostConnInfo[T], nextLevel: List[F[_]])

  /**
   * A context can be an adaptive concept, it could either be a parent node
   * inside some meta(higher level) information or a context when running.
   */
  case class HostConnC[F[+_], +C, +T](context: C, hc: HostConnInfo[T], nextLevel: List[F[_]])

  case class HostConnMat[F[+_], +T](parentSessionL: Option[SessionLayer], sl: SessionLayer, hm: HostConnInfoMat[T], nextLevel: List[F[_]])

  case class HFix[F[_[+_], +_], +A](unfix: F[HFix[F, +*], A])
  case class HCFix[F[_[+_], +_, +_], C, +A](unfix: F[HCFix[F, C, +*], C, A])
  case class HCDFix[F[_[+_, +_], +_, +_], +C, +A](unfix: F[λ[(+[C], +[D]) => HCDFix[F, C, D]], C, A])

  def ssh[A](host: String, port: Int, username: Option[String], password: Option[String], privateKey: Option[KeyPair], action: SshIO[A], children: HFix[HostConn, Any]*): HFix[HostConn, A] = {
    HFix(HostConn(HostConnInfo(host, port, username, password, privateKey, ScriptAction(action)), children.toList))
  }

  // def ssh[A](host: String, port: Int, username: Option[String], password: Option[String], privateKey: Option[KeyPair], action: => A, children: HFix[HostConn, Any]*): HFix[HostConn, A] = {
  //   HFix(HostConn(HostConnInfo(host, port, username, password, privateKey, ScriptAction(() => action)), children.toList))
  // }

  private[syntax] val d1 = HFix[HostConn, Any](
    HostConn(
      HostConnInfo(
        "192.168.99.100",
        2022,
        Some("test"),
        Some("test"),
        None,
        // ScriptAction(() => "88")
        ScriptAction(SshConn.scriptIO("hostname") *> SshConn.scriptIO("ls /"))
      ),
      List(
        HFix(
          HostConn(
            HostConnInfo(
              "192.168.99.100",
              2023,
              Some("test"),
              Some("test"),
              None,
              // ScriptAction(() => "")
              ScriptAction(SshConn.scpUploadIO("build.sbt"))
            ),
            List(
              HFix(
                HostConn(
                  HostConnInfo(
                    "192.168.99.100",
                    2023,
                    Some("test"),
                    Some("test"),
                    None,
                    // ScriptAction(() => "")
                    ScriptAction(SshConn.scpDownloadIO("/etc/issue"))
                  ),
                  Nil
                )
              )
            )
          )
        )
      )
    )
  )

  private[syntax] val d2: HFix[HostConn, Any] =
    ssh(
      "192.168.99.100",
      2022,
      Some("test"),
      Some("test"),
      None,
      SshConn.scriptIO("hostname") *> SshConn.scriptIO("ls /"),
      ssh(
        "192.168.99.100",
        2023,
        Some("test"),
        Some("test"),
        None,
        SshConn.scpUploadIO("build.sbt") *> SshConn.scpDownloadIO("/etc/issue")
        /*ssh(
          "192.168.99.100",
          2023,
          Some("test"),
          Some("test"),
          None,
          3
        )*/
      )
    )

  def main(args: Array[String]): Unit = {
    println(d1)
    println(d2)
  }
}
