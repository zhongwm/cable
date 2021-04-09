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

package zhongwm.cable.parser

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.TryValues._
import org.scalatest.OptionValues._
import org.scalatest.EitherValues._
import atto.Atto._
import atto.ParseResult.{Done, Fail, Partial}
import zhongwm.cable.parser.ProxyCommandParser.{cmdSegmentsP, cmdSegmentsToSshConfigHostItems}

class ProxyCommandParserSpec extends AnyFlatSpec with Matchers {
  val testdata =
    "sshpass -p 'proxyaw354w^&%pas_-od' ssh -q -vNJms -i ~/.ssh/id_rsa -l 18612341234 -W %h:%p -p 2022 192.168.100.12"
  val testdata2 =
    "sshpass -p 'proxyaw354w^&%pas_-od' ssh -q -vNJ ms -i ~/.ssh/id_rsa -l 18612341234 -W %h:%p -p 2022 192.168.100.12"
  val testdata3 =
    "sshpass -p 'proxyaw354w^&%pas_-od' ssh -q -vN -J ms -i ~/.ssh/id_rsa -l 18612341234 -W %h:%p -p 2022 192.168.100.12"

  "all test data" should "be successfully parsed" in {
    List(testdata, testdata2, testdata3) foreach { td =>
      val parsedValue = cmdSegmentsP.parseOnly(testdata)
      parsedValue match {
        case Done(input, rst) =>
          input should be ("")
          val sshConfigHostItem = cmdSegmentsToSshConfigHostItems(rst)
          sshConfigHostItem.success.value.privateKey.isDefined should be (true)
          sshConfigHostItem.success.value match {
            case HostItem(name, hostName, port, username, password, privateKey, proxyJumper, connectionTimeout) =>
              name.toString should be ("192.168.100.12")
              hostName.value should be (name.toString)
              port.value should be (2022)
              password.value should be ("proxyaw354w^&%pas_-od")
              privateKey.isDefined should be (true)
              proxyJumper.value match {
                case HostItem(name, hostName, port, username, password, privateKey, proxyJumper, connectionTimeout) =>
                  name.toString should be ("ms")
              }
          }
        case Fail(input, stack, message) =>
          fail("parse failed")
        case Partial(s) =>
          fail("Result says need more data, which should not happen.")
      }
    }
  }
}
