A scala library that practically connects the devops world and functional world.

# Constructing ssh tasks

## Static, by code

Statical definition of tasks, One benefits is that you get type information that reflects your
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

This case brings possibility for later handling of the task's result.

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
      scriptIO("hostname") *> scriptIO("ls /"),
      ssh(  // Here, the last parameter(of the topmost function), is a variable length Seq
        "192.168.99.100",
        2023,
        Some("test"),
        Some("test"),
        None,
        scpUploadIO("build.sbt") *> scpDownloadIO("/etc/issue")
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

Next: [Composing tasks to run in one host](Composing_tasks_to_run_in_one_host.md)