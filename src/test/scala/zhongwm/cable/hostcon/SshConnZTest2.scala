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

object SshConnZTest2 {
  val action =
    SshConn.scriptIO("hostname") <&>
      SshConn.scpUploadIO("build.sbt") <&
      SshConn.scpDownloadIO("/etc/issue")

  val layer1 =
    ((Blocking.live ++ SshConn.clientLayer) >>>
      SshConn.sessionL(
        new SshConn(Left("192.168.99.100", 2022), username = Some("test"), password = Some("test"))
      )) ++ Blocking.live

  val layer2 =
    (layer1 >>> SshConn.jumpAddressLayer("192.168.99.100", 2023)) ++ Blocking.live

  val layer3 =
    (layer2 >>> SshConn.jumpSshConnL(Some("test"), Some("test"))) ++ Blocking.live

  val layer4 = (SshConn.clientLayer ++ layer3) >>> SshConn.sessionL

  private val process = for {
    connJump <- SshConn.make(
                  Left("192.168.99.100", 2022),
                  username = Some("test"),
                  password = Some("test")
                )
    rst <- connJump.sessionM { outerSession =>
             SshConn.jumpTo("192.168.99.100", 2023)(outerSession) >>= { fwd =>
               val conn = new SshConn(Right(fwd.getBoundAddress), Some("test"), password = Some("test"))
               conn.sessionM { innerSession =>
                 SshConn.script("hostname")(innerSession) <&>
                   SshConn.scpUpload("build.sbt")(innerSession) <&
                   SshConn.scpDownload("/etc/issue")(innerSession)
               }
             }
           }
    _ <- putStrLn(rst._1._2._1.mkString)
    _ <- putStrLn(rst._1._2._2.mkString)
    xc <- ZIO.succeed {
            zio.ExitCode(rst._1._1)
          }
  } yield (xc)

  def main(args: Array[String]): Unit =
    Runtime.default.unsafeRun(action.provideCustomLayer(layer4))

}
