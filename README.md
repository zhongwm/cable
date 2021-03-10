# A scala ssh client library

## It's scala

Written with scala2

## A ssh client lib that is Functional, Effectful, Monadic

It's purely functional, effectful, monadic.

## Practical, Functionality rich

At the same time, it's functionality rich, task centric, concise, handy.
It supports ssh proxying, jumping over networks, tasks chaining.  

### Supports ssh proxying, in a monadic way!

Your host behind a bastion machine? no problem.
You have a series of remote task to deal with? no problem.
And connections are reused by multiple tasks for same machine.

#### Simple ssh task

```scala
  val simpleTask =
    Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), HostAction(scriptIO("ls /")))
```

#### Multiple ssh tasks example

```scala
  val simpleListSample =
    Action(HostConnInfo("192.168.99.100", 2022, Some("user"), None, Some(privateKey)), HostAction(scriptIO("cat /etc/issue"))) +:
    Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), HostAction(scpDownload("/etc/issue")))
```

#### Nested ssh tasks example

```scala
  val simpleNestedSample = Parental(
    JustConnect(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password"), None)),
      Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), HostAction(scpUpload("build.sbt")))
  )
```

#### Compound example

```scala
  val compoundSample =
    JustConnect(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password"))) +:
    Parental(
      JustConnect(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password"), None: Option[java.security.KeyPair])),
        Action(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password")), HostAction(scriptIO("hostname"))) +:
        Action(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password")), HostAction(scpUpload("build.sbt")))
    ) +:
    Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), HostAction(scpDownload("/etc/issue")))
```

[To get started](src/test/scala/zhongwm/cable/hostcon/EagerExecSpec.scala)

### Two Execution models

Two different execution model, eager and connection first. 
In connection first mode, all connections required are established before doing anything;
The other mode, eager, carries out the tasks promptly one by one.
 
### Resource Safe

As we can see in the previous sample code, we don't need to concern about connections' management, yet it's safely managed. 

Connections are guaranteed to be released correctly

## Full Support for ZIO programming

Full support for ZIO composition, ready to be embedded into ZIO project, 
compatible with ZIO ecosystem.

```scala
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