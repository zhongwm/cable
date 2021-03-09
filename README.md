# A scala ssh client library

## It's scala

Written with scala2

## A ssh client that is Functional, effectful, monadic

It's purely functional

## Concise, handy
### Supports ssh proxying, in a monadic way!

```scala
  val simpleData =
    Action(HostConnInfo("192.168.99.100", 2023, Some("test"), Some("test")), ScriptAction(scriptIO("hostname")))

  val simpleListedSample =
    Action(HostConnInfo("192.168.99.100", 2022, Some("test"), Some("test")), ScriptAction(scriptIO("hostname"))) +:
    Action(HostConnInfo("192.168.99.100", 2023, Some("test"), Some("test")), ScriptAction(scriptIO("hostname")))

  val simpleNestedSample = Parental(
    JustConnect(HostConnInfo("192.168.99.100", 2022, Some("test"), Some("test"), None)),
    Action(HostConnInfo("192.168.99.100", 2023, Some("test"), Some("test")), ScriptAction(scriptIO("hostname")))
  )
  val compoundSample =
    JustConnect(HostConnInfo("192.168.99.100", 2022, Some("test"), Some("test"))) +:
      Parental(
        JustConnect(HostConnInfo("192.168.99.100", 2022, Some("test"), Some("test"), None: Option[java.security.KeyPair])),
        Action(HostConnInfo("192.168.99.100", 2022, Some("test"), Some("test")), ScriptAction(scriptIO("hostname"))) +:
          Action(HostConnInfo("192.168.99.100", 2022, Some("test"), Some("test")), ScriptAction(scriptIO("hostname")))
      ) +:
      Action(HostConnInfo("192.168.99.100", 2023, Some("test"), Some("test")), ScriptAction(scriptIO("hostname"))) +:
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