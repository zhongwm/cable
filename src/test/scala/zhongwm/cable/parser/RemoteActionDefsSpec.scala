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
 *
 * by Zhongwenming<br>
 */

package zhongwm.cable.parser

import org.scalatest.matchers.should
import org.scalatest.wordspec.AnyWordSpec
import pprint.{log => println}
import zhongwm.cable.parser.RemoteActionFileDefs.IniLex._
import zhongwm.cable.parser.RemoteActionFileDefs.PRGroup

class RemoteActionDefsSpec extends AnyWordSpec with should.Matchers {
  private val value1  = "[Group1]"
  private val value2 = "[Group1_nx:vars]"

  private var value2_2 =
    """[Group1_nx:vars]
      |ansible_become=yes
      |ansible_ssh_common_args="-o ControlMaster=auto -o ControlPersist=10m UserKnownHostsFile=<> -o GlobalKnownHostsFile=<>" """.stripMargin

  private val value2_3 = "192.168.1.2 aasgd_gas=easd ansible_ssh_common_args=\"-o ControlMaster=auto -o ControlPersist=10m UserKnownHostsFile=<> -o GlobalKnownHostsFile=<>\" s=b"
  private val value2_4 = "192.168.1.2 ansible_ssh_common_args='-o ControlMaster=auto -o ControlPersist=10m UserKnownHostsFile=<> -o GlobalKnownHostsFile=<>'"

  private val value3 = List(
    "[Group1]",
    "host1 ansible_host=192.168.1.53 ansible_password=\"123ads\"",
    "192.168.31.1 ansible_password=\"zxzzsd\"",
    "",
    "www.github.com ",
    " ",
    "[global:vars]",
    "ansible_become=yes",
    "ansible_become_exe=sudo"
  )

  private val inventoryFiles = "ansible_playbooks/BACKUP_hostsfile_128_45.yml" ::
    "ansible_playbooks/BACKUP_hostsfile_8_110.yml" :: Nil


  """ "[Group1]" """ should {
    "not be group vars" in {
      isGroupVarSec(value1) shouldBe ("", false)
    }

    "be a group" in {
      isAGroupLine(value1) shouldBe ("Group1", true)
    }
  }

  """ [Group1_nx:vars] """ should {
    "be a group" in {
      isAGroupLine(value2) shouldBe ("Group1_nx:vars", true)
    }

    "be a group vars def" in {
      val rv1 = isAGroupLine(value2)
      if (rv1._2) isGroupVarSec(value2) shouldBe ("Group1_nx", true)
    }
  }

  "a host def line " should {
    "be parsed properly" in {
      val pr1 = HostLineP.parseHostLine(PRGroup("phantom"), value2_3)
      println(pr1)
      pr1.isRight shouldBe true
    }
  }

  "String list of a group hosts definition" should {
    "be parsed normally" in {
      val parsedGroup = parseMerged(value3)
      println(parsedGroup)
    }
  }

  "inventoryFiles" should {
    "be parsed normally" in {
      inventoryFiles.foreach{f=>
        getClass.getClassLoader.getResource(f).getFile
      }
    }
  }

}