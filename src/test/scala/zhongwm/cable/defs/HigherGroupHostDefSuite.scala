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

import zio.test._
import Assertion._
import cats._
import cats.implicits._
import HigherGroupHostDefSuite.higherGroupHostDefsSpec

object HigherGroupHostDefSuite {
  import zhongwm.cable.parser.HigherGroupHosts._
  private[defs] val higherGroupHostDefsSpec = suite("highergrouphost with env ") {
    test("envGroupHostAttrs should be full cfg") {
      val intermediate = for {
      e <- envGroupHostAttrs

      } yield {e._1.toList.map{_.isLower}}
      val thatValue = envGroupHostAttrs("ansible_become")
      println(thatValue)
      assert(intermediate.toList.flatten.exists(_ === false))(isTrue) &&
      assert(envGroupHostAttrs.size)(isGreaterThanEqualTo(10)) &&
        assert{thatValue.get.isInstanceOf[Boolean]}(isTrue) &&
        assert(thatValue.get.asInstanceOf[Boolean])(isFalse) &&
      assert(envGroupHostAttrs("ansible_port").get.isInstanceOf[Int])(isTrue) &&
      assert(envGroupHostAttrs("ansible_port").get.asInstanceOf[Int])(equalTo(22)) &&
      assert(envGroupHostAttrs.getOrElse("ansible_any", "NotAKey").asInstanceOf[String])(equalTo("NotAKey"))
    }
  }
}

object HigherGroupHostSuiteGroup extends DefaultRunnableSpec {
  override def spec = suite("all")(higherGroupHostDefsSpec)
}


