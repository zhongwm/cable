<div style="text-align: right;">Next: <a href="Composing_tasks_to_run_in_one_host.md">Composing tasks to run in one host</a></div>

---

# Constructing ssh tasks

## Static, by code

With statical definition of tasks, one benefits is that you get type information that reflects your
task's structure.


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

The inferred type, which can be auto completed by your ide, is `Unit +: (Nested[Unit, ((Int,
(Chunk[String], Chunk[String])), (Int, (Chunk[String], Chunk[String])))], ((Int, (Chunk[String],
Chunk[String])), Unit))`

This paradigm brings possibility for later handling of the task's result.

## Dynamic, by parser

Dynamic creation of tasks, you can generate tasks from config file, like ansible book

```scala
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
      Some(FactAction("just echoing last fact", Zssh.sshIoFromFacts(m=>Zssh.scriptIO(s"echo Displaying fact value: ${m("hostNameOfA").asInstanceOf[SshScriptIOResult].stdout.mkString}")))),  // Could be set to None to opt out doing anything.
    ),
    ssh(
      "192.168.99.100",
      2023,
      Some("test"),
      Some("test"),
      None,
      Some(FactAction("Chained at same level", Zssh.sshIoFromFacts(m=>Zssh.scriptIO(s"echo What we got: ${m("just echoing last fact").asInstanceOf[SshScriptIOResult].stdout.mkString}")))),  // Could be set to None to opt out doing anything.
    ),
  )
```

---

<div style="text-align: right;">Next: <a href="Composing_tasks_to_run_in_one_host.md">Composing tasks to run in one host</a></div>