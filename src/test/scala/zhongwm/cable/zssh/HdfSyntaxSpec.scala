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
import zhongwm.cable.zssh.Zssh.types._
import zhongwm.cable.zssh.hdfsyntax.Hdf._
import zhongwm.cable.zssh.hdfsyntax.HdfSyntax._

class HdfSyntaxSpec extends AnyWordSpec with Matchers {

  import SshActionDef._
  
  "Script" when {
    "executed" should {
      "be ok" in {
        val value = executeSshIO(script)
        println(value)
        value.isRight should be(true)
      }
    }

    "Some random test code" should {
      "be ok" in {
        val initCtx: Option[HostConnInfo[_]] = None
        val result = hostConn2HostConnC(script, initCtx, Some(_))
        println(s"value: $result")
        implicit val layered: HCFix[HostConnC, Option[SessionLayer], _] = toLayered(result)
        // val hcf = implicitly[HCFunctor[HostConnC, Option[SessionLayer]]](layered)
        println(s"layered: $layered")
        val materialized = hcFold(execWithContext(ZsshContext.empty), layered)
        println(materialized)

        hFold(inspection, script)
      }
    }
  }
}
