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
    Action("192.168.99.100", Some(2023), Some("test"), Some("test"), action =
      scriptIO("hostname") <&>
      scpUploadIO("build.sbt") <&
      scpDownloadIO("/etc/issue")
    )

  val flatListTask =
    Action.asFact("192.168.99.100", Some(2022), Some("test"), Some("test"), factName = "hostNameA", action=scriptIO("sleep 5; hostname")) +:
    Action("192.168.99.100", Some(2023), Some("test"), Some("test"), action = sshIoFromFactsM(m => scriptIO(s"echo The last fact we got is ${m("hostNameA")}")) <& scpDownloadIO("/etc/issue"))

  val simpleNestedTask = Parental(
    // JustConnect("192.168.99.100", Some(2022), Some("test"), Some("test")),
    JustConnect("testHostJumped", password = Some("test")),
    Action("testHost2", password = Some("test"), action = scriptIO("hostname"))
  )
  val compoundSample =
    Action.asFact("192.168.99.100", Some(2022), Some("test"), Some("test"), factName = "TheHostNameOfA", action = scriptIO("hostname")) +:
      Parental(
        JustConnect("192.168.99.100", Some(2023), Some("test"), Some("test")),
        Action("192.168.99.100", Some(2022), Some("test"), Some("test"), action = scriptIO("hostname")) +:
          Action("192.168.99.100", Some(2023), Some("test"), Some("test"), action = sshIoFromFactsM(d => scriptIO(s"echo The last fact we got is ${d("TheHostNameOfA")}")) <*> scriptIO("echo Current host is $(hostname)"))
      ) +:
      Action("192.168.99.100", Some(2023), Some("test"), Some("test"), action = scriptIO("hostname")) +:
      HCNil


  val script =
    ssh(
      "192.168.99.100",
      Some(2022),
      Some("test"),
      Some("test"),
      factName = Some("versionOfLinux"),
      action = Zssh.scriptIO("hostname") *> Zssh.scriptIO("echo /etc/issue"),
      children = ssh(
        "192.168.99.100",
        Some(2023),
        Some("test"),
        Some("test"),
        factName = Some("versionOfLinuxOf2ndHost"),
        action = Zssh.scpUploadIO("build.sbt") *> Zssh.scpDownloadIO("/etc/issue"),
        children = HCNil
      ) +:
        ssh(
          "192.168.99.100",
          Some(2023),
          Some("test"),
          Some("test"),
          action = Zssh.scpUploadIO("build.sbt") *> Zssh.scpDownloadIO("/etc/issue"),
          children = HCNil
        )
    )

  "EagerExec" when {
    "execute" should {
      "succeed" in {
        println(simpleNestedTask.hc)
        val result: NestedC[Unit, types.SshScriptIOResult] = simpleNestedTask.runI()
        val listResult = zio.Runtime.default.unsafeRun(ZIO.effect{flatListTask.run()}.either)
        println(result.result)
        println(listResult.isRight)
        listResult.map{r => println(s"${r.a.result}, ${r.b.result}")}
        listResult.map{r => println(r.a.result.map(_.stdout.mkString))}
        listResult.map{r =>
          r.b.result.map(_.succeeded) shouldBe Some(true)
        }
      }
    }

    "execute compoundSample" should {
      "succeed" in {
        val value1 = compoundSample.run()
        val v_b = value1.b
        v_b.result.foreach{v =>
          val vv = v._1
          println(vv.child._1)
          println(vv.child._2)
        }
      }
    }
  }

}
