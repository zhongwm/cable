# A scala ssh client library

## A ssh client lib that is Functional, Monadic

It's scala, It's purely functional, monadic.

## Practical, Functionality rich

At the same time, it's functionality rich, task centric, succinct, handy.
It supports ssh proxying, jumping over networks, tasks chaining.  

#### Installation

```scala
libraryDependencies += "io.github.zhongwm" %% "cable" % "0.3.0"
```

### Supports ssh proxying, in a monadic way!

Your host behind a bastion machine? no problem.
You have a series of remote task to deal with? no problem.
And connections are reused by multiple tasks for same machine.

A dsl to represent composite ssh tasks.

#### Simple ssh task

```scala
import zhongwm.cable.zssh.TypeDef._
import HostConnS._
import zhongwm.cable.zssh.Zssh._
......
  val simpleTask =
    Action("192.168.99.100", 2023, "user", "password", scriptIO("sleep 5; ls /"))
```
      
#### Simple ssh task with multiple tasks on a same host or connection.
```scala
  val simpleData =
    Action(
      "192.168.99.100", 2023, "user", "password",
      scriptIO("hostname") <&>
        scpUploadIO("build.sbt") <&
        scpDownloadIO("/etc/issue")
    )

```

#### Multiple ssh tasks example

```scala
  val simpleListTasks =
    Action("192.168.99.100", 2022, "user", Some(privateKey), scriptIO("cat /etc/issue")) +:
    Action("192.168.99.100", 2023, "user", "password", scpDownloadIO("/etc/issue"))
```

#### Nested ssh tasks example

```scala
  val simpleNestedTasks = Parental(
    JustConnect("192.168.99.100", 2022, "user", "password"),
      Action("192.168.99.100", 2023, "user", "password", scpUploadIO("build.sbt"))
  )
```

#### Compound example

```scala
  val compoundTasks =
    JustConnect("192.168.99.100", 2022, "user", "password") +:
    Parental(
      JustConnect("192.168.99.100", 2022, "user", "password" ),
        Action("192.168.99.100", 2022, "user", "password", scriptIO("hostname")) +:
        Action("192.168.99.100", 2022, "user", "password", scpUploadIO("build.sbt"))
    ) +:
    Action("192.168.99.100", 2023, "user", "password", scpDownloadIO("/etc/issue"))
```

### Running

Tap on `run` to fire task execution. Result types are inferred and reflecting the task composition
structure.

```scala
val nestedResult = simpleNestedTasks.run() // Inferred type: NestedC[Unit, (Int, (Chunk[String], Chunk[String]))]
val listResult = simpleListTasks.run()     // Inferred type: (Int, (Chunk[String], Chunk[String])) +|: (Int, (Chunk[String], Chunk[String]))
```

### Resource Safe

As we can see in the previous sample code, we don't need to concern about connections' management, yet it's safely managed. 

Connections are guaranteed to be released correctly

#### Docs

Docs here [wiki](wiki/Contents.md)

[To get started](src/test/scala/zhongwm/cable/zssh/ExecSpec.scala)

## Full Support for ZIO programming

Full support for ZIO composition, ready to be embedded into ZIO project, 
compatible with ZIO ecosystem.

```scala
  val action = {
    scriptIO("hostname") <&>
      scpUploadIO("build.sbt") <&
      scpDownloadIO("/etc/issue")
  }
val jumperLayer = Zssh.sessionL("192.168.99.100", 2022, username = Some("test"), password = Some("test"))

  val jumpedLayer =
    Zssh.jumpSessionL(jumperLayer, "192.168.99.100", 2023, Some("test"), Some("test"))

  val layer2 =
    ((jumperLayer ++ Blocking.live) >>> Zssh.jumpAddressLayer("192.168.99.100", 2023)) ++ Blocking.live

  val layer3 = layer2 >>> Zssh.jumpSshConnL(Some("test"), Some("test"))

  val layer4 = (Zssh.clientLayer ++ layer3 ++ Blocking.live) >>> Zssh.sessionL

```

```scala
private val process = for {
    connJump <- Zssh.make(
                  Left("192.168.99.100", 2022),
                  username = Some("test"),
                  password = Some("test")
                )
    rst      <- connJump.sessionM { outerSession =>
                  Zssh.jumpTo("192.168.99.100", 2023)(outerSession) >>= { fwd =>
                    val conn = Zssh(Right(fwd.getBoundAddress), Some("test"), password = Some("test"))
                    conn.sessionM { innerSession =>
                      Zssh.script("hostname")(innerSession) <&>
                      Zssh.scpUpload("build.sbt")(innerSession) <&
                      Zssh.scpDownload("/etc/issue")(innerSession)
                    }
                  }
                }
    _        <- putStrLn(rst._1._2._1.mkString)
    _        <- putStrLn(rst._1._2._2.mkString)
    xc       <- ZIO.succeed {
                  zio.ExitCode(rst._1._1)
                }
  } yield xc
```

## Efficient, fast

Based on mina-sshd-netty

## P.S.

This project is greatly inspired by a famous python project [ansible](https://ansible.com), which is
a very famous project in devops. This project strives to join the functional world and the devops
world in the field of remote host related tasks. Not all of them, not all of ansible, but in some way.