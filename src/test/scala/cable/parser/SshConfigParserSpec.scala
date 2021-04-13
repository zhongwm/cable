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

package cable.parser

import atto.ParseResult.{Done, Fail, Partial}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues._
import org.scalatest.OptionValues._
import SshConfigParser.{configRegistry, hostTitleLineP, parseUserSshConfig, sshConfigHostItemToHostItem, sshConfigItemP}
import atto.Atto._

import java.nio.file.Paths
import scala.util.Using

class SshConfigParserSpec extends AnyFlatSpec with Matchers {
  "when parsing given ssh config snippet" should "succeed" in {

    val hostTitle =
      """Host wms-jumper
        |""".stripMargin
    print("1 ")
    println(hostTitleLineP.parse(hostTitle).done)

    val testdata1 = """Host ms
                      |        HostName 10.8.128.45
                      |        # sdaf
                      |        ProxyCommand sshpass -p 'jkyyoljkn75*&' ssh -l 18612341234 -W %h:%p -p 4088 192.168.100.21
                      |        Port 323""".stripMargin
    val done1     = sshConfigItemP.parse(testdata1).done
    done1 match {
      case Done(input, result) =>
        println(input)
        println(result)
      case Fail(input, stack, message) =>
        println(input)
        println(stack)
        println(message)
        fail("Parsing failed.")
      case Partial(k) =>
        fail("The result says need more data, which should not happen.")
    }
  }

  "when parsing user .ssh config" should "success" in {
    val r = parseUserSshConfig()
    if (r.isFailure) {
      r.failure.exception.printStackTrace()
    }
    r.success.value.hostItems should not be (Nil)
    r.success.value.hostItems.foreach(println)

    r.foreach{a => sshConfigHostItemToHostItem(a.hostItems).foreach(println)}
  }

  "app when started" should "have a valid sshConfigRegistry" in {
    configRegistry.value.hostItems.nonEmpty should be (true)
    println(configRegistry.value.hostItems)
  }
}
