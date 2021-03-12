import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.5"
  val catsVersion = "2.4.2"
  val zioVersion = "1.0.5"
  val sshdVersion = "2.6.0"

  lazy val commonDependencies = Seq(
    scalaTest % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.lihaoyi" %% "pprint" % "0.5.6" % "test",
    "org.tpolecat" %% "atto-core" % "0.7.0",
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
    "dev.zio" %% "zio-test-magnolia" % zioVersion % "test",
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.apache.sshd" % "sshd-netty" % sshdVersion,
    "org.apache.sshd" % "sshd-cli" % sshdVersion,
    "net.i2p.crypto" % "eddsa" % "0.3.0"
  )
}
