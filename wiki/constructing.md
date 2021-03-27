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
  val script =
    ssh(
      "192.168.99.100",
      2022,
      Some("test"),
      Some("test"),
      None,
      Some(FactAction("some-fact", scriptIO("hostname") *> scriptIO("ls /"))),  /* or None */  // When we don't want to execute anything on the jumper, just specify None.
      ssh(  // Here, the last parameter(of the topmost function), is a variable length Seq
        "192.168.99.100",
        2023,
        Some("test"),
        Some("test"),
        None,
        Some(SshAction(scpUploadIO("build.sbt") *> scpDownloadIO("/etc/issue"))), // Carry out some task here.
        ssh(
          "192.168.99.100",
          2023,
          Some("test"),
          Some("test"),
          None,
          3
        )
      )
    )
```

---

<div style="text-align: right;">Next: <a href="Composing_tasks_to_run_in_one_host.md">Composing tasks to run in one host</a></div>