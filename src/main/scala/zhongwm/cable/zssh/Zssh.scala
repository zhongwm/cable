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

package zhongwm.cable.zssh
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileInputStream, IOException, OutputStream, PipedInputStream, PipedOutputStream}
import java.net.{InetSocketAddress, SocketAddress}
import java.nio.charset.StandardCharsets
import java.nio.file.Paths
import java.security.{KeyPair, PublicKey}
import java.util
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.{ChannelExec, ClientChannel, ClientChannelEvent}
import org.apache.sshd.client.keyverifier.ServerKeyVerifier
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.client.session.forward.{ExplicitPortForwardingTracker, PortForwardingTracker}
import org.apache.sshd.common.future.CloseFuture
import org.apache.sshd.common.util.net.SshdSocketAddress
import cats.implicits._
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.apache.sshd.scp.client.ScpClientCreator
import org.apache.sshd.common.channel.PtyMode
import zhongwm.cable.core.LogbackConfig
import zio._
import zio.blocking._
import zio.console.{Console => ZConsole}
import zio.console._
import zio.stream._
import zio.duration._

import scala.io.Source

case class Zssh(
               connInfo: Either[(String, Int), SshdSocketAddress],
               username: Option[String] = Some("root"),
               password: Option[String] = None,
               privateKey: Option[KeyPair] = None
             ) {
  import Zssh.log

  def mapToIOE[R, A](z: ZIO[R, Throwable, A]): ZIO[R, IOException, A] =
    z.mapError {
      case ex: IOException =>
        ex
      case ex =>
        new IOException("Non-IOException made IOE", ex)
    }

  def sessionM[R, A](routine: Function[ClientSession, ZIO[R, IOException, A]]) =
    for (
      _sess <- ZIO.environment[Has[SshClient]] >>= { cli =>
        val _client = cli.get
        effectBlocking {
          val connFuture = connInfo match {
            case Left(pair) =>
              _client.connect(username.getOrElse("root"), pair._1, pair._2)
            case Right(sock) =>
              _client.connect(username.getOrElse("root"), sock.toInetSocketAddress)
          }
          val session = connFuture.verify(8000).getSession

          password.foreach(session.addPasswordIdentity)
          privateKey.foreach(session.addPublicKeyIdentity)
          session.auth().verify()
          session
        }.mapError {
          case ex: IOException =>
            ex
          case ex =>
            if (log.isWarnEnabled) log.warn(s"It's odd that zio throws an exception other than IOException: ${ex}")
            new IOException(ex)
        }.bracket { x =>
          ZIO.effectTotal {
            x.close()
            if (log.isDebugEnabled) log.debug("Session closed")
          }
        } {
          routine(_)
        }
      }
    ) yield _sess

  def withSessionM[A](routine: ZIO[Has[ClientSession], IOException, A]) =
    routine provideLayer sessionLayer

  def sessionLayer =
    ZLayer.fromManaged(ZManaged.make(for {
      hasCli <- ZIO.environment[Has[SshClient]]
      sess <- mapToIOE {
        effectBlocking {
          val _client = hasCli.get
          val connFuture = connInfo match {
            case Left(pair) =>
              _client.connect(username.getOrElse("root"), pair._1, pair._2)
            case Right(sock) =>
              _client.connect(username.getOrElse("root"), sock.toInetSocketAddress)
          }
          val session = connFuture.verify(8000).getSession

          password.foreach(session.addPasswordIdentity)
          privateKey.foreach(session.addPublicKeyIdentity)
          session.auth().verify()
          session
        }
      }
    } yield sess) { s =>
      ZIO.effectTotal(s.close())
    })

}

object Zssh {

  LogbackConfig.configWarnLogbackForLib()

  val log = LoggerFactory.getLogger(s"${classOf[Zssh].getPackage.getName}.Zssh")

  object types {
    /**
     * `Nothing with Has[ClientSession]`, not `Any with Has[ClientSession]`
     *
     * And mixing a Nothing is required.
     *
     * {{{
     *   implicitly[ZIO[Blocking with Has[ClientSession], IOException, String] <:< HostConnInfoMat[String]]
     *   implicitly[ZIO[Has[ClientSession], IOException, String] <:< ZIO[Nothing with Has[ClientSession], IOException, String]]
     *
     *   implicitly[String with Any <:< String]
     *   implicitly[String <:< String with Any]
     *   implicitly[String =:= String with Any]
     *   // implicitly[String =:= String with Nothing]// can't prove that
     *
     *   implicitly[Function[String, Any with Int] <:< Function[String, Int]]
     *   implicitly[Function[String, Any with Int] =:= Function[String, Int]]
     *
     *   implicitly[Function[String, Int] <:< Function[Nothing with String, Int]]
     *   // implicitly[Function[String, Int] =:= Function[Nothing with String, Int]] // can't prove that
     *
     *   case class AAA(v: HostConnInfoMat[String])
     *   def aaa(p: ZIO[Int with Has[ClientSession], IOException, String]) = {
     *     val hcim: HostConnInfoMat[String] = p
     *   }
     * }}}
     *
     * @tparam A
     */
    type HostConnInfoMat[+A] = ZIO[Blocking with ZConsole with Has[ClientSession], IOException, A]

    type SessionLayer = ZLayer[Blocking, IOException, Has[ClientSession]]

    type KeyPair = java.security.KeyPair

    type SshIO[+A] = ZIO[ZEnv with Has[ClientSession] with Has[ZsshContext], IOException, A]

    type SshScriptIOResult = (Int, (Chunk[String], Chunk[String]))

    val ev1 = implicitly[ZIO[Blocking with Has[ClientSession], IOException, Int] <:< SshIO[Int]]

    implicit class SshScriptIOResultT(r: SshScriptIOResult) {
      val exitCode: Int = r._1
      val stdout: Chunk[String] = r._2._1
      val stderr: Chunk[String] = r._2._2
    }
  }

  implicit val clientLayer: ZLayer[Blocking, Nothing, Has[SshClient]] =
    ZLayer fromManaged Managed.make {
      UIO.succeed {
        val client = SshClient.setUpDefaultClient()
        client.setServerKeyVerifier(new ServerKeyVerifier() {
          override def verifyServerKey(
                                        clientSession: ClientSession,
                                        remoteAddress: SocketAddress,
                                        serverKey: PublicKey
                                      ): Boolean =
            true
        })
        client.start()
        client
      }
    } { c: SshClient =>
      effectBlocking {
        c.stop()
        if (log.isDebugEnabled) log.debug("Stopped client.")
        c.close()
        if (log.isDebugEnabled) log.debug("Closed Client.")
      }.either
    }

  def make(
            addr: Either[(String, Int), SshdSocketAddress],
            username: Option[String] = Some("root"),
            password: Option[String] = None,
            privateKey: Option[KeyPair] = None
          ): UIO[Zssh] =
    IO.succeed(Zssh(addr, username, password, privateKey))

  def sessionL(host: String, port: Int, username: Option[String] = Some("root"), password: Option[String], privateKey: Option[KeyPair] = None): ZLayer[Blocking, IOException, Has[ClientSession]] =
    sessionL(Left(host, port), username, password, privateKey)

  def sessionL(connInfo: Either[(String, Int), SshdSocketAddress], username: Option[String], password: Option[String], privateKey: Option[KeyPair]): ZLayer[Blocking, IOException, Has[ClientSession]] =
    (clientLayer ++ Blocking.live) >>> ZLayer.fromAcquireRelease(ZIO.environment[Has[SshClient]] >>= { cli =>
      val sshConn = Zssh(connInfo, username, password, privateKey)
      effectBlocking {
        val _client = cli.get
        val connFuture = sshConn.connInfo match {
          case Left(pair) =>
            _client.connect(sshConn.username.getOrElse("root"), pair._1, pair._2)
          case Right(sock) =>
            _client.connect(sshConn.username.getOrElse("root"), sock.toInetSocketAddress)
        }
        val session = connFuture.verify(8000).getSession

        sshConn.password.foreach(session.addPasswordIdentity)
        sshConn.privateKey.foreach(session.addPublicKeyIdentity)
        session.auth().verify()
        session
      }.mapError {
        case ex: IOException =>
          ex
        case ex =>
          if(log.isWarnEnabled) log.warn(s"It's odd that zio throws an exception other than IOException: ${ex}")
          new IOException(ex)
      }
    }) { x =>
      ZIO.effectTotal {
        x.close()
        if (log.isDebugEnabled) log.debug("Session closed")
      }
    }

  val sessionL = ZLayer.fromManaged(ZManaged.make(ZIO.environment[Has[SshClient] with Has[Zssh]] >>= { r =>
    mapToIOE(effectBlocking {
      val _client = r.get[SshClient]
      val sshConn = r.get[Zssh]
      val connFuture = sshConn.connInfo match {
        case Left(pair) =>
          _client.connect(sshConn.username.getOrElse("root"), pair._1, pair._2)
        case Right(sock) =>
          _client.connect(sshConn.username.getOrElse("root"), sock.toInetSocketAddress)
      }
      val session = connFuture.verify(8000).getSession

      sshConn.password.foreach(session.addPasswordIdentity)
      sshConn.privateKey.foreach(session.addPublicKeyIdentity)
      session.auth().verify()
      session
    })
  }) { x =>
    ZIO.effectTotal {
      x.close()
      if (log.isDebugEnabled) log.debug("Session closed")
    }
  })

  def mapToIOE[R, A](z: ZIO[R, Throwable, A]): ZIO[R, IOException, A] =
    z.mapError {
      case ex: IOException =>
        ex
      case ex =>
        new IOException("Non-IOException made IOE", ex)
    }

  def jumpLayer(targetIp: String, targetPort: Int) =
    ZLayer.fromAcquireRelease {
      for {
        cs <- ZIO
          .environment[Has[ClientSession]]
        fwd <- mapToIOE(effectBlocking {
          val localFwTracker = cs.get.createLocalPortForwardingTracker(
            SshdSocketAddress.LOCALHOST_ADDRESS,
            new SshdSocketAddress(targetIp, targetPort)
          )
          localFwTracker
        })
      } yield fwd
    }(j => ZIO.effect(j.close()).either)

  def jumpAddressLayer(targetIp: String, targetPort: Int) =
    jumpLayer(targetIp, targetPort) >>>
      ZLayer.fromFunction((x: Has[ExplicitPortForwardingTracker]) => x.get.getBoundAddress)

  def jumpSessionL(
        zl: ZLayer[Blocking, IOException, Has[ClientSession]],
        host: String, port: Int,
        username: Option[String] = None,
        password: Option[String] = None,
        privateKey: Option[KeyPair] = None) =
    ((((Blocking.live ++ zl) >>> jumpAddressLayer(host, port) ++ Blocking.live) >>>
      jumpSshConnL(username, password, privateKey)) ++ Blocking.live ++ clientLayer) >>>
        sessionL

  def jumpSshConnL(username: Option[String], password: Option[String] = None, privateKey: Option[KeyPair] = None) =
    ZLayer.fromService { a: SshdSocketAddress =>
      Zssh(Right(a), username, password, privateKey)
    }

  def withJumpM[A](targetIp: String, targetPort: Int, jumpWork: ZIO[Has[SshdSocketAddress], IOException, A]) =
    jumpWork provideLayer {
      jumpAddressLayer(targetIp, targetPort)
    }

  def shellM(cmd: String)(cs: ClientSession) =
    for {
      cmdOutput <- ZIO
        .effect(cs.createShellChannel())
        .mapError {
          case ex: IOException =>
            ex
          case ex =>
            new IOException("Non-IOException caught.", ex)
        }
        .bracket { sh =>
          ZIO.effect(sh.close()).catchAll(_ => URIO.succeed(()))
        } { x =>
          effectBlocking {
            x.setIn(new ByteArrayInputStream(cmd.getBytes(StandardCharsets.UTF_8)))
            val out = new ByteArrayOutputStream
            x.setOut(out)
            x.setErr(out)
            x.open().await()
            x.waitFor(util.EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EOF), 0L)
            (x.getExitStatus, new String(out.toByteArray, StandardCharsets.UTF_8))
          }.mapError {
            case ex: IOException =>
              ex
            case ex: Throwable =>
              new IOException("Non IOException in interaction.", ex)
          }
        };
      _ <- putStrLn(cmdOutput.toString)
    } yield (cmdOutput)

  def scriptIO(cmd: String) =
    for {
      cs <- ZIO.environment[Has[ClientSession]]
      r  <- script(cmd)(cs.get)
    } yield r

  def script(cmd: String)(cs: ClientSession) =
    for {
      setup <- effectBlocking {
        if (log.isDebugEnabled) log.debug(s"Executing command $cmd")
        val ch = cs.createExecChannel(cmd)
        ch.setUsePty(false)
        val pos  = new PipedOutputStream
        val pis  = new PipedInputStream(pos)
        val peos = new PipedOutputStream
        val peis = new PipedInputStream(peos)
        //            val out = new ByteArrayOutputStream
        //            val errOut = new ByteArrayOutputStream
        if (log.isDebugEnabled) log.debug("Created streams")
        ch.setOut(pos)
        ch.setErr(peos)
        if (log.isDebugEnabled) log.debug("Open channel and wait.")
        ch.open().await()
        (ch, pos, pis, peos, peis)
      }.mapError {
        case ex: IOException =>
          ex
        case ex =>
          new IOException("Non-IOException made IOE", ex)
      }
      outS <- (ZIO.bracket(ZIO.succeed(setup)) { c =>
          ZIO.effect {
            if (log.isDebugEnabled) log.debug("Reclaiming io resources.")
            c._1.close()
          }.orElse(putStrLn("Closing channel encountered an error.")) *>
            ZIO.effect {
          c._2.flush()
          c._2.close()
        }
          .orElse(putStrLn("Flushing and closing a PipedOutputStream encountered an error.")) *>
            ZIO.effect {
          c._3.close()
        }.orElse(putStrLn("Closing a PipedInputStream encountered an error.")) *>
            ZIO.effect {
          c._4.flush()
          c._4.close()
        }.orElse(putStrLn("Flushing and Closing a PipedOutputStream encountered an error.")) *>
            ZIO.effect {
          c._5.close()
        }.orElse(putStrLn("Closing a PipedInputStream encountered an error."))
      } { ct =>
        val c = ct._1
        val ss1 = Stream
          .fromInputStream(ct._3)
          .aggregate(Transducer.utf8Decode)
          .aggregate(Transducer.splitLines)
          .mapM { v =>
            IO.effect(if (log.isDebugEnabled) log.debug(s"output1: $v")).ignore *> UIO.succeed(v)
          } // .schedule(Schedule.fixed(100.milliseconds))
        val ss2 = Stream
          .fromInputStream(ct._5)
          .aggregate(Transducer.utf8Decode)
          .aggregate(Transducer.splitLines)
          .mapM { v =>
            IO.effect(if (log.isDebugEnabled) log.debug(s"output2: $v")).ignore *> UIO.succeed(v)
          }
        ss1.runCollect <*> ss2.runCollect // ss1.merge(ss2).runCollect
      }).mapError {
        case e: IOException =>
          e
        case t: Throwable =>
          new IOException(t)
        case a: Any =>
          new IOException(s"Cause: `${a.getClass.getCanonicalName}`: $a")
      }
      _ <- ZIO.effect{if (log.isDebugEnabled) log.debug("Begin receiving from streams")}.ignore *> mapToIOE(effectBlocking {
        if (log.isDebugEnabled) log.debug("Waiting for event.")
        setup._1.waitFor(
          util.EnumSet.of(ClientChannelEvent.CLOSED, ClientChannelEvent.EOF, ClientChannelEvent.EXIT_STATUS),
          0L
        )
      })
      rc <- ZIO.succeed(setup._1.getExitStatus.intValue())
    } yield (rc, outS)

  def scpUploadIO(path: String, targetPath: Option[String] = None) =
    for {
      cs <- ZIO.environment[Has[ClientSession]]
      r  <- scpUpload(path, targetPath)(cs.get)
    } yield r

  def scpUpload(path: String, targetPath: Option[String] = None)(cs: ClientSession) =
    for {
      sc <- mapToIOE(ZIO.effect {
        val scpCreator = ScpClientCreator.instance()
        val sc         = scpCreator.createScpClient(cs)
        // doo stuff here.
        sc.upload(path, targetPath.getOrElse("/tmp"))
      })
    } yield sc

  def scpDownloadIO(path: String, targetPath: Option[String] = None) =
    for {
      cs <- ZIO.environment[Has[ClientSession]]
      r  <- scpDownload(path, targetPath)(cs.get)
    } yield r

  def scpDownload(path: String, targetPath: Option[String] = None)(cs: ClientSession) =
    for {
      sc <- mapToIOE(ZIO.effect {
        val scpCreator = ScpClientCreator.instance()
        val sc         = scpCreator.createScpClient(cs)
        sc.download(path, Paths.get("."))
      })
    } yield sc

  def jumpTo(targetIp: String, targetPort: Int)(
    cs: ClientSession
  ): ZIO[Blocking, IOException, ExplicitPortForwardingTracker] =
    for {
      ch <- effectBlocking {

        val localFwTracker = cs.createLocalPortForwardingTracker(
          SshdSocketAddress.LOCALHOST_ADDRESS,
          new SshdSocketAddress(targetIp, targetPort)
        )
        val address = localFwTracker.getBoundAddress
        if (log.isDebugEnabled) log.debug(s"Local forward is open, ${localFwTracker.isOpen}")
        if (log.isDebugEnabled) log.debug(s"Local bound address: ${address}")
        if (log.isDebugEnabled) log.debug(s"Local address: ${localFwTracker.getBoundAddress}")
        //        val channel = cs.createDirectTcpipChannel(
        //          new SshdSocketAddress(SshdSocketAddress.LOCALHOST_IPV4, 52323),
        //          new SshdSocketAddress(targetIp, targetPort))
        //        channel.open.verify(3000L)
        //        println("Local port is: " + channel.getLocalSocketAddress.getPort)
        //        channel
        localFwTracker
      }.mapError {
        case ex: IOException =>
          ex
        case ex =>
          new IOException("Non-IOException caught.", ex)
      }.bracket(f => ZIO.effect(f.close()).ignore)(UIO.succeed(_))
    } yield ch

  def sessionFinalizer(session: ClientSession) =
    UIO.effectTotal(session.close(true))

  def channelFinalizer(tc: ClientChannel) =
    Task.effect(tc.close(true).some).catchAll { _ =>
      ZIO.none
    }

  def clientFinalizer(tcli: SshClient): UIO[CloseFuture] =
    UIO.effectTotal(tcli.close(true))

  def forwardingTrackerFinalizer(forwarder: PortForwardingTracker) =
    UIO.effectTotal(forwarder.close())

  def main(args: Array[String]): Unit = {

    val runtime2 = Runtime.unsafeFromLayer(ZEnv.live >+> clientLayer)
    val conn     = Zssh(Left("192.168.99.100", 2022), password = Some("test"), username = Some("test"))
    val result: (Int, (Chunk[String], Chunk[String])) = runtime2.unsafeRun(
      conn
        .sessionM(script( /*"ls /;exit\n"*/ "ls -l /"))
        .catchAll(e => ZIO.succeed(3, (Chunk(""), Chunk(s"${e.getMessage}"))))
    )
    runtime2.shutdown()
    println(result._1)
    println(result._2._1.toList.mkString("\n"))
    println(result._2._2.toList.mkString("\n"))
  }

  def main2(args: Array[String]): Unit = {
    val runtime = Runtime.unsafeFromLayer(ZEnv.live)
    val t = for {
      s <- ZIO.effect {
        new FileInputStream("/tmp/adobegc.log")
      }
        .bracket(
          in =>
            ZIO.effect {
              if (log.isDebugEnabled) log.debug("Releasing InputStream")
              in.close()
            }.ignore,
          in =>
            ZIO.bracket {
              ZIO.effect {
                Source.fromInputStream(in)
              }
            } { source =>
              ZIO.effectTotal {
                source.close()
              } >>= { _ =>
                putStrLn("Releasing source.")
              }
            } { source =>
              val eff = ZIO.effect(source.getLines().toList.head)
              for {
                str1 <- eff
                _    <- putStrLn("End!")
              } yield (str1)
            }
        )
      _ <- putStrLn(s)
    } yield (s)
    runtime.unsafeRun(t)
    runtime.shutdown()
  }

}

