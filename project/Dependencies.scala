import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.2.5"
  val catsVersion = "2.4.2"
  val zioVersion = "1.0.5"
  val sshdVersion = "2.6.0"

  lazy val commonDependencies = Seq(
    scalaTest % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.tpolecat" %% "atto-core" % "0.7.0",
    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "com.lihaoyi" %% "pprint" % "0.5.6" % "test",
    "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
    "dev.zio" %% "zio-test-magnolia" % zioVersion % "test",
    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    "org.apache.sshd" % "sshd-netty" % sshdVersion,
    "org.apache.sshd" % "sshd-cli" % sshdVersion,
    "net.i2p.crypto" % "eddsa" % "0.3.0",
    "com.google.guava" % "guava" % "30.1.1-jre",
    "io.github.zhongwm.commons" % "javasecurityio" % "0.1.1"
  )
}
