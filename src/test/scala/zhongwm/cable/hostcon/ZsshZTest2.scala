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

import zio._
import zio.blocking._
import zio.console._

object ZsshZTest2 {
  val action = {
    import Zssh._
    scriptIO("hostname") <&>
      scpUploadIO("build.sbt") <&
      scpDownloadIO("/etc/issue")
  }

  val jumperLayer = Zssh.sessionL(
        Left("192.168.99.100", 2022), username = Some("test"), password = Some("test")
      )

  val jumpedLayer =
    Zssh.jumpSessionL(jumperLayer, "192.168.99.100", 2023, Some("test"), Some("test"))


  
  val layer2 =
    ((jumperLayer ++ Blocking.live) >>> Zssh.jumpAddressLayer("192.168.99.100", 2023)) ++ Blocking.live

  val layer3 = layer2 >>> Zssh.jumpSshConnL(Some("test"), Some("test"))

  val layer4 = (Zssh.clientLayer ++ layer3 ++ Blocking.live) >>> Zssh.sessionL


  private val process = for {
    connJump <- Zssh.make(
                  Left("192.168.99.100", 2022),
                  username = Some("test"),
                  password = Some("test")
                )
    rst <- connJump.sessionM { outerSession =>
             Zssh.jumpTo("192.168.99.100", 2023)(outerSession) >>= { fwd =>
               val conn = Zssh(Right(fwd.getBoundAddress), Some("test"), password = Some("test"))
               conn.sessionM { innerSession =>
                 Zssh.script("hostname")(innerSession) <&>
                   Zssh.scpUpload("build.sbt")(innerSession) <&
                   Zssh.scpDownload("/etc/issue")(innerSession)
               }
             }
           }
    _ <- putStrLn(rst._1._2._1.mkString)
    _ <- putStrLn(rst._1._2._2.mkString)
    xc <- ZIO.succeed {
            zio.ExitCode(rst._1._1)
          }
  } yield xc

  def main(args: Array[String]): Unit =
    Runtime.default.unsafeRun(action.provideCustomLayer(jumpedLayer))

}
