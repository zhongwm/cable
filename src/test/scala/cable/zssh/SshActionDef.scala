/*
 * Copyright 2020-2021, Wenming Zhong
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

package cable.zssh

import cable.zssh.hdfsyntax.Hdf._
import zio._
import Zssh.types._
import cable.zssh.hdfsyntax.Hdf.HostConn
import TypeDef.Host

object SshActionDef {

  val script =
    ssh(
      "192.168.99.100",
      Some(2022),
      Some("test"),
      Some("test"),
      privateKey = None,
      None,  // Or you can do something on the bastion host by saying: Some(SshAction(Zssh.scpDownloadIO("/etc/issue") *> Zssh.scriptIO("uname -r") *> Zssh.scriptIO("hostname"))
      children = Seq(ssh(
        "192.168.99.100",
        Some(2023),
        Some("test"),
        Some("test"),
        None,
        action = Some(SshAction(Zssh.scpDownloadIO("/etc/issue") *> Zssh.scriptIO("ls /"))),  // Could be set to None to opt out doing anything.
      ))
    )

  val script2 =
    ssh(
      "192.168.99.100",
      Some(2022),
      Some("test"),
      Some("test"),
      action = Some(FactAction("hostNameOfA", Zssh.scpDownloadIO("/etc/issue") *> Zssh.scriptIO("uname -r") *> Zssh.scriptIO("hostname"))),
      children = Seq(ssh(
        "192.168.99.100",
        Some(2023),
        Some("test"),
        Some("test"),
        action = Some(FactAction("just echoing last fact", Zssh.sshIoFromFactsM(d=>Zssh.scriptIO(s"echo Displaying fact value: ${d("hostNameOfA")}, current host: `hostname`")))),  // Could be set to None to opt out doing anything.
      ),
      ssh(
        "192.168.99.100",
        Some(2023),
        Some("test"),
        Some("test"),
        action = Some(FactAction("Chained at same level", Zssh.sshIoFromFactsM(d=>Zssh.scriptIO(s"echo What we got: ${d("just echoing last fact")}")))),  // Could be set to None to opt out doing anything.
      ),
      )
    )

  val scriptManualDef = HFix[HostConn, Any](
    HostConn(
      HostNop(
        Host("192.168.99.100",
        Some(2022),
        Some("test"),
        Some("test"),
        None),
        // ScriptAction(() => "88")
        // SshAction(Zssh.scriptIO("hostname") *> Zssh.scriptIO("ls /"))
      ),
      List(
        HFix(
          HostConn(
            HostAction(
              Host("192.168.99.100",
              Some(2023),
              Some("test"),
              Some("test"),
              None),
              // ScriptAction(() => "")
              SshAction(Zssh.scpUploadIO("build.sbt") *> Zssh.scpDownloadIO("/etc/issue"))
            ),
            Nil
          )
        )
      )
    )
  )

  val scriptManualDef3Tier = HFix[HostConn, Any](
    HostConn(
      HostAction(
        Host("192.168.99.100",
        Some(2022),
        Some("test"),
        Some("test"),
        None),
        // ScriptAction(() => "88")
        SshAction(Zssh.scriptIO("hostname") *> Zssh.scriptIO("ls /"))
      ),
      List(
        HFix(
          HostConn(
            HostAction(
              Host("192.168.99.100",
              Some(2023),
              Some("test"),
              Some("test"),
              None),
              // ScriptAction(() => "")
              SshAction(Zssh.scpUploadIO("build.sbt") *> Zssh.scpDownloadIO("/etc/issue"))
            ),
            List(
              HFix(
                HostConn(
                  HostAction(
                    Host("192.168.99.100",
                    Some(2023),
                    Some("test"),
                    Some("test"),
                    None),
                    // ScriptAction(() => "")
                    SshAction(Zssh.scpDownloadIO("/etc/issue"))
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
}
