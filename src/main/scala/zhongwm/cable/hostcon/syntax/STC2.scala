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

object STC2 {

  sealed trait ScaAnsible[A]
  case class ScriptAction[A](script: () => A) extends ScaAnsible[A]

  case class HostConnInfo[A](ho: String,
                             port: Int,
                             userName: String = "root",
                             password: Option[String],
                             privKey: Option[String],
                             action: ScaAnsible[A])
  case class HostConn[F[_], T](hc: HostConnInfo[T], nextLevel: List[F[Any]])

  case class HFix[F[_[_], _], A](unfix: F[HFix[F, *], A])

  val d1 = HFix[HostConn, String](
    HostConn(
      HostConnInfo(
        "192.168.99.100",
        2022,
        "test",
        Some("test"),
        None,
        ScriptAction(() => "88")
      ),
      List(
        HFix(
          HostConn(
            HostConnInfo(
              "192.168.99.100",
              2023,
              "test",
              Some("test"),
              None,
              ScriptAction(() => true)
            ),
            List(
              HFix(
                HostConn(
                  HostConnInfo(
                    "192.168.99.100",
                    2023,
                    "test",
                    Some("test"),
                    None,
                    ScriptAction(() => 3)
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


  

  def main(args: Array[String]): Unit = {
    println(d1)
    
  }
}
