import Dependencies._

// library name
name := "cable"

// library version
ThisBuild / version := "0.1.0"

// groupId, SCM, license information
organization := "io.github.zhongwm"
homepage := Some(url("https://github.com/zhongwm/cable"))
credentials += Credentials(Path.userHome / ".sbt" / "sonatype_credentials")
scmInfo := Some(ScmInfo(url("https://github.com/zhongwm/cable"), "git@github.com:zhongwm/cable.git"))
developers := List(Developer("zhongwenming", "Wenming Zhong", "zhongwm@gmail.com", url("https://zhongwm.github.io")))
licenses += "PRIVATE" -> url("https://raw.githubusercontent.com/zhongwm/zhongwm.github.io/master/LICENSE.txt")

ThisBuild / pomIncludeRepository := { _ => false }
ThisBuild / publishMavenStyle := true

// disable publishing the api docs jar
Compile / packageDoc / publishArtifact := true
// disable publishing the main sources jar
Compile / packageSrc / publishArtifact := true

// disable publish ith scala version, otherwise artifact name will include scala version
// e.g cable_2.13
crossPaths := true

// add sonatype repository settings
// snapshot versions publish to sonatype snapshot repository
// other versions publish to sonatype staging repository
ThisBuild / publishTo := Some(
  if (isSnapshot.value)
    Opts.resolver.sonatypeSnapshots
  else
    Opts.resolver.sonatypeStaging
)


///////////
// Resolvers, use sftp, comment out if not used.
val sftpResolver = Resolver.sftp("sftphost", sys.env.getOrElse("sftpmvnhost", "localhost"), "/tmp/repo/")
resolvers += sftpResolver
// publishTo := Some(sftpResolver)
///////////


scalaVersion := "2.13.5"
addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.3" cross CrossVersion.full)
addCompilerPlugin("org.typelevel" % "kind-projector" % "0.11.3" cross CrossVersion.full)
libraryDependencies ++= (scalaBinaryVersion.value match {
    case "2.10" =>
        compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full) :: Nil
    case _ =>
        Nil
})
libraryDependencies ++= commonDependencies
