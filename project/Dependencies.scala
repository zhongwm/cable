import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.1.1"
  val akkaVersion = "2.6.12"
  val akkaHttpVersion = "10.2.3"
  val doobieVersion = "0.9.0"
  val scalazVersion = "7.3.2"
  val catsVersion = "2.3.1"
  val kittensVersion = "2.2.1"
  val http4sVersion = "0.21.7"
  val zioVersion = "1.0.4-2"
  val circeVersion = "0.13.0"

  lazy val commonDependencies = Seq(
    scalaTest % Test,
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-distributed-data" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-persistence-typed" % akkaVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
    //lightbend commercial stuff:
    "com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % "1.0.9", //
    "com.lightbend.akka.management" %% "akka-management-cluster-http" % "1.0.9",
    "com.typesafe.akka" %% "akka-discovery" % akkaVersion,
    "com.lightbend.akka.discovery" %% "akka-discovery-consul" % "1.0.8",

    "com.lihaoyi" %% "pprint" % "0.5.6" % "test",

  //    "com.pszymczyk.consul" % "embedded-consul" % "2.1.4",
    "com.orbitz.consul" % "consul-client" % "1.4.2",


    "org.tpolecat" %% "doobie-core"     % doobieVersion,
    "org.tpolecat" %% "doobie-specs2"   % doobieVersion,
    //      "org.tpolecat" %% "dobbie-scalatest" % doobieVersion % Test,
    //      "org.tpolecat" %% "dobbie-hikari" % doobieVersion,

    "com.lmax" % "disruptor" % "3.4.2",
    "org.tpolecat" %% "atto-core" % "0.7.0",

    "dev.zio" %% "zio" % zioVersion,
    "dev.zio" %% "zio-streams" % zioVersion,
    "dev.zio" %% "zio-test-sbt" % zioVersion % "test",
    "dev.zio" %% "zio-test-magnolia" % zioVersion % "test",
    "dev.zio" %% "zio-process" % "0.0.5",
    "dev.zio" %% "zio-nio" % "1.0.0-RC9",
    "dev.zio" %% "zio-interop-cats" % "2.1.4.0",

    "org.http4s" %% "http4s-blaze-server" % http4sVersion,
    "org.http4s" %% "http4s-circe" % http4sVersion,
    "org.http4s" %% "http4s-dsl" % http4sVersion,

    "org.typelevel" %% "cats-core" % catsVersion,
    "org.typelevel" %% "cats-free" % catsVersion,
    
    "org.typelevel" %% "kittens" % kittensVersion,

    "org.apache.sshd" % "sshd-netty" % "2.4.0",
    "com.jcraft" % "jsch" % "0.1.55",
    //      "org.apache.sshd" % "sshd-mina" % "2.4.0",
    "org.apache.sshd" % "sshd-cli" % "2.4.0",
    "net.i2p.crypto" % "eddsa" % "0.3.0",

    "com.github.pureconfig" %% "pureconfig" % "0.13.0",
    "io.circe" %% "circe-core" % circeVersion,
    "io.circe" %% "circe-yaml" % "0.13.1",
    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-generic-extras" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,


    "mysql" % "mysql-connector-java" % "8.0.18",
    //      "mysql" % "mysql-connector-java" % "6.0.6",

    "com.michaelpollmeier" %% "gremlin-scala" % "3.4.7.2",
    "org.apache.tinkerpop" % "tinkergraph-gremlin" % "3.4.7",

    "org.ahocorasick" % "ahocorasick" % "0.4.0"
  )

  lazy val slickDependencies = Seq(
    "com.typesafe.slick" %% "slick" % "3.3.2",
    "com.typesafe.slick" %% "slick-hikaricp" % "3.3.2",
    "com.typesafe.slick" %% "slick-codegen" % "3.3.2"
  )

  lazy val scalazDependencies = Seq(
    "org.scalaz" %% "scalaz-core" % scalazVersion,
    "org.scalaz" %% "scalaz-effect" % scalazVersion,
    "org.scalaz" %% "scalaz-scalacheck-binding" % scalazVersion % "test"
  )
}
