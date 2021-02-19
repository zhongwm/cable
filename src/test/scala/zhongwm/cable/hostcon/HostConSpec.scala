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
import org.apache.sshd.client.SshClient
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.BeforeAndAfter
import zio._
import zio.console._

class HostConSpec extends AnyWordSpec with BeforeAndAfter {
  val runtime: Runtime.Managed[zio.ZEnv with Has[SshClient]] =
    Runtime.unsafeFromLayer(ZEnv.live >+> SshConn.clientZLayer)

  before {}

  after {
    runtime.shutdown()
  }

  "SshConn" when {
    "Connecting to ssh host" should {
      val conn = new SshConn(
        Left("192.168.99.100", 2022),
        password = Some("test"),
        username = Some("test")
      )

      val result: (Int, (Chunk[String], Chunk[String])) = runtime.unsafeRun(
        conn
          .sessionM(conn.script( /*"ls /;exit\n"*/ "ls -l /"))
          .catchAll(e => ZIO.succeed(3, (Chunk(""), Chunk(s"${e.getMessage}"))))
      )

      "be ok" in {
        println(result._1)
        println(result._2._1.toList.mkString("\n"))
        println(result._2._2.toList.mkString("\n"))
      }
    }

    "Connecting to ssh host monadic" should {
      "be ok" in {
        val process = for {
          connJump <- SshConn.io(
            Left("192.168.99.100", 2022), username = Some("test"), password = Some("test"),
          )
          rst <- connJump.sessionM { outerSession =>
            SshConn.jumpTo("192.168.99.100", 2023)(outerSession) >>= {fwd=>
                val conn = new SshConn(Right(fwd.getBoundAddress), Some("test"), password = Some("test"))
                conn.sessionM { innerSession =>
                  conn.script("hostname")(innerSession) <&>
                    conn.scpUpload("build.sbt")(innerSession) <&
                    conn.scpDownload("/etc/issue")(innerSession)
                }
              }
          }
          _ <- putStrLn(rst._1._2._1.mkString)
          _ <- putStrLn(rst._1._2._2.mkString)
          xc <- ZIO.succeed {
            zio.ExitCode(rst._1._1)
          }
        } yield (xc)
        runtime.unsafeRun(process)
      }
    }
  }
}
