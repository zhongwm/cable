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
import cats.{Id, ~>}

object Stc {

  /**
    *
    * hostconn {
    *   action1 {
    *
    *   }
    *
    *   action2 {
    *   }
    *
    *   nestedHostconn {
    *
    *    innerAction2 {
    *    }.fork
    *
    *    innerAction3 {
    *
    *    }.fork
    *
    *    join
    *
    *    innerAction4 {
    *    }
    *
    *   }
    *
    * }
    *
    */
  sealed trait ScaAnsible[A]
  case class ScriptAction[A](script: String) extends ScaAnsible[A]

  case class HostConnInfo(ho: String,
                          port: Int,
                          userName: String = "root",
                          password: Option[String],
                          privKey: Option[String],
                          //action: ScaAnsible[String]
  )
  case class HostConn[A](hc: HostConnInfo, nextLevel: List[A])

  case class Fix[F[_]](unfix: F[Fix[F]])
  case class HFix[F[_], A](result: A, unfix: F[HFix[F, A]])

  val hostConnInfo1 = HostConnInfo(
    "192.168.99.100",
    2022,
    "test",
    Some("test"),
    None,
    //ScriptAction[String]("who am i")
  )

  val hostConnInfo2: HostConnInfo = HostConnInfo(
    "192.168.99.100",
    2023,
    "test",
    Some("test"),
    None,
    // ScriptAction[String]("hostname")
  )

  val hostConn1: Fix[HostConn] = Fix(HostConn(hostConnInfo1, Nil))

  val hostConn2: Fix[HostConn] = Fix(HostConn(hostConnInfo2, Nil))

  val hostConns1Fixed = Fix[HostConn](
    HostConn(
      hostConnInfo1,
      List(
        Fix(HostConn(hostConnInfo2, List(Fix(HostConn(hostConnInfo2, Nil)))))
      )
    )
  )

  def main(args: Array[String]): Unit = {
    println(hostConns1Fixed)
  }

}
