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
 *
 * by Zhongwenming<br>
 */

package zhongwm.cable.defs
import zhongwm.cable.parser.Defs._
import zio.test._
import zio.console._
import Assertion._

object InilexTest {
  private val inventoryFiles = "ansible_playbooks/BACKUP_hostsfile_128_45.yml" ::
    "ansible_playbooks/BACKUP_hostsfile_8_110.yml" :: Nil

  private[defs] val inilexSuite = suite("inilex") (
    inventoryFiles.map { f =>
      testM(s"parsing a normal inventory ini file $f should be get some groups and hosts") {
        assertM(readInventoryFile(getClass.getClassLoader.getResource(f).getFile))(hasField("groupName", (ag: AGroup) => ag.groupName, equalTo("all")))
      }
    }: _*
  )

  private[defs] val  inilexSuite2 = suite("inilexWithInspect")(
    inventoryFiles.map {f =>
      testM(s"Parsing a normal inventory ini file $f should return some meaningful groups") {
        for {
        ag <- readInventoryFile(getClass.getClassLoader.getResource(f).getFile)
        _ <-  putStrLn(pprint.tokenize(ag).mkString)
        } yield assert(ag.groupName)(equalTo("all"))
      }
    }: _*
  )
}

object AllSuites extends DefaultRunnableSpec {
  import InilexTest._
  def spec = suite("All tests")(inilexSuite, inilexSuite2)
}
