Previous: [Jump over proxy in cable](Jump_over_proxy_in_cable.md)

# Piping streams with remote task

When using SSH with bash scripting, people often make use of the UNIX pipe.

To get the pipe mechanism works in Cable, we do:

```scala
scriptIO(cmd: String, inputStream: InputStream)
```

And yes, there are 3 more overloads

```scala
scriptIO(cmd: String, inputAsString: String)

scriptIO(cmd: String, input: Chunk[String])

scriptIO(cmd: String, fileInput: File)

scriptIO(cmd: String, inputData: ByteBuffer)

```

As an example, we pipe data to remote host tasks like this:

```scala
import cable.zssh.TypeDef._
import HostConnS._
import cable.zssh.Zssh._

val putFileI = Action("my-server", action = scriptIO("cat -", new File("My file.txt")))
val putStringI = Action("my-server", action = scriptIO("cat -", "String data"))
val putStream = Action("my-server", action = scriptIO("cat -", inputStream))
```

When it comes to the Resource Management, what we do about the inputStream? Well, roughly speaking,
as a well accepted convention, whoever creates it is responsible for closing it. So we close them by
we calling-side ourselves.

Moreover, in the above task constructions we just name the host to connect to, in this case
"my-server", and leave the username, password or private key can be omitted, these default to
current system user and private key, which is just like the way SSH does, if you get your machine's
ssh-copy-id -ish thing configured, indeed you can just name the host to connect to.
