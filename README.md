# A scala ssh client library

## It's scala

Written with scala2

## A ssh client that is Functional, effectful, monadic

It's purely functional

## Concise, handy
### Supports ssh proxying, in a monadic way!

```scala
    connJump <- Zssh.make(
          Left("192.168.99.100", 2022), username = Some("test"), password = Some("test"),
        )
    rst     <- connJump.sessionM { outerSession =>
      Zssh.jumpTo("192.168.99.100", 2023)(outerSession) >>= { fwd=>
        val conn = new Zssh(Right(fwd.getBoundAddress), Some("test"), password = Some("test"))
        conn.sessionM { innerSession =>
          script("hostname")(innerSession) <&>
            scpUpload("build.sbt")(innerSession) <&
            scpDownload("/etc/issue")(innerSession)
        }
      }
    }
    _       <- putStrLn(rst._1._2._1.mkString)
    _       <- putStrLn(rst._1._2._2.mkString)
```

### Resource Safe

As we can see in the previous sample code, we don't need to concern about connections' management, yet it's managed. 

Connections are guaranteed to be released correctly

## Supports ZIO style programming

Full support for ZIO composition, ready to be embedded into ZIO project, 
compatible with ZIO ecosystem.



## Efficient, high performant

Based on mina-sshd-netty