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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import TypeDef._
import Zssh._
import HostConnS._
import zio.{Chunk, ZIO}
import Evaluation._

class ExecSpec extends AnyWordSpec with Matchers {
  
  val simpleTask =
    Action("192.168.99.100", 2023, "test", "test",
      scriptIO("hostname") <&>
      scpUploadIO("build.sbt") <&
      scpDownloadIO("/etc/issue")
    )

  val flatListTask =
    Action("192.168.99.100", 2022, "test", "test", scriptIO("sleep 5; hostname")) +:
    Action("192.168.99.100", 2023, "test", "test", scriptIO("hostname") &> scpDownloadIO("/etc/issue"))

  val simpleNestedTask = Parental(
    JustConnect("192.168.99.100", 2022, "test", "test"),
    Action("192.168.99.100", 2023, "test", "test", scriptIO("hostname"))
  )
  val compoundSample =
    JustConnect("192.168.99.100", 2022, "test", "test") +:
      Parental(
        JustConnect("192.168.99.100", 2022, "test", "test"),
        Action("192.168.99.100", 2022, "test", "test", scriptIO("hostname")) +:
          Action("192.168.99.100", 2022, "test", "test", scriptIO("hostname"))
      ) +:
      Action("192.168.99.100", 2023, "test", "test", scriptIO("hostname")) +:
      HCNil


  val script =
    ssh(
      "192.168.99.100",
      2022,
      Some("test"),
      Some("test"),
      None,
      Zssh.scriptIO("hostname") *> Zssh.scriptIO("ls /"),
      ssh(
        "192.168.99.100",
        2023,
        Some("test"),
        Some("test"),
        None,
        Zssh.scpUploadIO("build.sbt") *> Zssh.scpDownloadIO("/etc/issue"),
        HCNil
      ) +:
        ssh(
          "192.168.99.100",
          2023,
          Some("test"),
          Some("test"),
          None,
          Zssh.scpUploadIO("build.sbt") *> Zssh.scpDownloadIO("/etc/issue"),
          HCNil
        )
    )

  "EagerExec" when {
    "execute" should {
      "succeed" in {
        val result: NestedC[Unit, types.SshScriptIOResult] = simpleNestedTask.runI()
        val listResult = zio.Runtime.default.unsafeRun(ZIO.effect{flatListTask.run()}.either)
        println(result.result)
        println(listResult.isRight)
        listResult.map{r => println(r.a.result.map(_.stdout))}
        listResult.map{r =>
          r.b.result.map(_.succeeded) shouldBe Some(true)
        }
      }
    }
  }

}
