# A scala ssh client library

## It's scala

Written with scala2

## A ssh client that is Functional, effectful, monadic

It's purely functional

## Concise, handy
### Supports ssh proxying, in a monadic way!

#### Simple ssh task

```scala
  val simpleData =
    Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), ScriptAction(scriptIO("hostname")))
```

#### Simple multiple ssh tasks sample

```scala
  val simpleListSample =
    Action(HostConnInfo("192.168.99.100", 2022, Some("user"), None, Some(privateKey)), ScriptAction(scriptIO("hostname"))) +:
    Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), ScriptAction(scriptIO("hostname")))
```

#### Simple nested ssh tasks sample

```scala
  val simpleNestedSample = Parental(
    JustConnect(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password"), None)),
    Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), ScriptAction(scriptIO("hostname")))
  )
```

#### Compound sample

```scala
  val compoundSample =
    JustConnect(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password"))) +:
      Parental(
        JustConnect(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password"), None: Option[java.security.KeyPair])),
        Action(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password")), ScriptAction(scriptIO("hostname"))) +:
          Action(HostConnInfo("192.168.99.100", 2022, Some("user"), Some("password")), ScriptAction(scriptIO("hostname")))
      ) +:
      Action(HostConnInfo("192.168.99.100", 2023, Some("user"), Some("password")), ScriptAction(scriptIO("hostname"))) +:
      HCNil
```

[To get started](src/test/scala/zhongwm/cable/hostcon/EagerExecSpec.scala)

### Resource Safe

As we can see in the previous sample code, we don't need to concern about connections' management, yet it's managed. 

Connections are guaranteed to be released correctly

## Supports ZIO style programming

Full support for ZIO composition, ready to be embedded into ZIO project, 
compatible with ZIO ecosystem.

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

## Efficient, high performant

Based on mina-sshd-netty