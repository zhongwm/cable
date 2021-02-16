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

package zhongwm.cable.core
import java.io.{ByteArrayInputStream, FileInputStream}

import cats._
import cats.implicits._
import zio._
import zio.stream._
import zio.console._

object ZIS {
  def main(args: Array[String]): Unit = {
    case class Exam(person: String, score: Int)



    val examResults = Seq(
      Exam("Alex", 64),
      Exam("Michael", 97),
      Exam("Bill", 77),
      Exam("John", 78),
      Exam("Bobby", 71)
    )

    val groupByKeyResult: ZStream[Any, Nothing, (Int, Int)] =
      Stream
        .fromIterable(examResults)
        .groupByKey(exam => exam.score / 10 * 10) {
          case (k, s) => Stream.fromEffect(s.runCollect.map(l => k -> l.size))
        }

    val s = Stream.fromInputStream(new FileInputStream("/tmp/adobedebug.log"))

    val data = Seq("lines", "sdf", "dfs").mkString("\n")
    Chunk(data)

    val t = s.transduce(Transducer.utf8Decode)
    val t2 = t.transduce(Transducer.splitLines)
    val t3 = t2.mapConcatChunk(Chunk(_))
    val t2_2 = t2.tap(data =>putStrLn(data)) //.partition(isError, 4)
    /*
    ZTransducer.splitLines.push.use {push =>
      for {
      x <- push(Chunk(data).some)
      } yield()

    }*/

    val groupedResult: ZStream[Any, Nothing, Chunk[Int]] =
      Stream
        .fromIterable(0 to 100)
        .grouped(50)
  }
}
