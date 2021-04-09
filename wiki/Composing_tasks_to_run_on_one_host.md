Previous: [Constructing ssh tasks](constructing.md)

---

# Composing tasks to run inside a host

To compose tasks to run inside a host, we use [zio](https://zio.dev),

```scala
import zhongwm.cable.zssh.TypeDef._
import zhongwm.cable.zssh.TypeDef.HostConnS._
import zhongwm.cable.zssh.Zssh._
......
  val action = {
    scriptIO("hostname") <&>         // One script
      scpUploadIO("build.sbt") <&    // 
      scpDownloadIO("/etc/issue")
  }
```

The first `scriptIO("...")` says to executed a script / command in some host, `<&>` means run the
following task at the same time (in parallel). The following `scpUploadIO("...")` means to upload a
file from local file system to via scp to the remote host (the same host as the scriptIO one). 
Likely the `scpDownloadIO("...")` means to download a file from the remote host.

Thees 3 functions ends with IO have a type, We call it an IO, an effect, actually they are totally
legal zio effects, whose formal type are some [zio](https://zio.dev)`.ZIO`.
                              
For those whom are unfamiliar with zio, ZIO effects compose in several ways. While the `<&>` means
to run in parallel, also in semantic it means to compose the result of this operation and the result
of the other operand, the composed one. The composition result is also a ZIO, whose result will be
the tuple product of the two component effect.

The other compositing operator `<&` and `&>`, also joins the other task in parallel, but the discards
one of the two components, respectively to opposite of the direction of the arrow.

Likely, there is another [series of
operators](https://zio.dev/docs/overview/overview_basic_operations#zipping), `<*>`, `<*`, `*>`,
which are all sequential combine operators.

```scala
  val action = {
    scriptIO("hostname") <*>         // Here <*>, not to comfuse with <&>
      scpUploadIO("build.sbt") <*    // 
      scpDownloadIO("/etc/issue")
  }
```

As for the tasks result requirement of your need, These operators has the same result preservation
property as the aforementioned series.

---

Previous: [Constructing ssh tasks](constructing.md)