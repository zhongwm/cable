<div style="text-align: right;">Next: <a href="Composing_tasks_to_run_on_one_host.md">Composing tasks to run on one host</a></div>

---

# Constructing ssh tasks

## Static, by code

With statical definition of tasks, one benefits is that you get type information that reflects your
task's structure.


```scala
import zhongwm.cable.zssh.TypeDef._
import zhongwm.cable.zssh.TypeDef.HostConnS._
import zhongwm.cable.zssh.Zssh._
...
  val compoundTask =
  Action("192.168.99.100", 2022, "test", "test", "TheHostNameOfA", scriptIO("hostname")) +:
    Parental(
      JustConnect("192.168.99.100", 2023, "test", "test"),
      Action("192.168.99.100", 2022, "test", "test", scriptIO("hostname")) +:
        Action("192.168.99.100", 2023, "test", "test", sshIoFromFactsM(d => scriptIO(s"echo The last fact we got is ${d("TheHostNameOfA")}")) <*> scriptIO("echo Current host is $(hostname)"))
    ) +:
    Action("192.168.99.100", 2023, "test", "test", scriptIO("hostname")) +:
    HCNil
```

The inferred type, which can be auto completed by your ide, is `Unit +: (Nested[Unit, ((Int,
(Chunk[String], Chunk[String])), (Int, (Chunk[String], Chunk[String])))], ((Int, (Chunk[String],
Chunk[String])), Unit))`

With `Parental` construct, we place a nested ssh tasks composing, parent level acts as the jumper host for the child tasks, also
parent level tasks get gets executed before the latter.

To get multiple tasks on different hosts executed one after another, chain them using `+:`.

This paradigm brings possibility for later handling of the task's result.

## Dynamic, by parser

Dynamic creation of tasks, you can generate tasks from config file, like ansible book

```scala
import zhongwm.cable.zssh._
import zhongwm.cable.zssh.hdfsyntax.Hdf._
import zhongwm.cable.zssh.hdfsyntax.HdfSyntax._
import zhongwm.cable.zssh.Zssh._
......
  val script2 =
  ssh(
    "192.168.99.100",
    2022,
    Some("test"),
    Some("test"),
    None,
    Some(FactAction("hostNameOfA", Zssh.scpDownloadIO("/etc/issue") *> Zssh.scriptIO("hostname"))),   // Could be set to None to opt out doing anything.
    ssh(
      "192.168.99.100",
      2023,
      Some("test"),
      Some("test"),
      None,
      Some(FactAction("just echoing last fact", Zssh.sshIoFromFacts(d=>Zssh.scriptIO(s"echo Displaying fact value: ${d("hostNameOfA")}")))),  // Could be set to None to opt out doing anything.
    ),
    ssh(
      "192.168.99.100",
      2023,
      Some("test"),
      Some("test"),
      None,
      Some(FactAction("Chained at same level", Zssh.sshIoFromFacts(d=>Zssh.scriptIO(s"echo What we got: ${m("just echoing last fact")}")))),  // Could be set to None to opt out doing anything.
    ),
  )
```

---

<div style="text-align: right;">Next: <a href="Composing_tasks_to_run_on_one_host.md">Composing tasks to run in one host</a></div>