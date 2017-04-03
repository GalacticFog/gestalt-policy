import com.typesafe.sbt.packager.docker._

name := """gestalt-policy"""

version := "0.1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala,SbtNativePackager)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws
)

dockerBaseImage := "java:8-jre-alpine"

dockerCommands := dockerCommands.value.flatMap {
  case cmd@Cmd("FROM",_) => List(
    cmd,
    Cmd("RUN", "apk add --update bash && rm -rf /var/cache/apk/*")     
  )
  case other => List(other)
}

maintainer in Docker := "Brad Futch <brad@galacticfog.com>"


libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.6.1"

libraryDependencies += "com.galacticfog" %% "gestalt-lambda-io" % "0.3.0-SNAPSHOT" withSources()

libraryDependencies += "com.galacticfog" %% "gestalt-meta-sdk-scala" % "0.3.1-SNAPSHOT" withSources()

libraryDependencies += "com.galacticfog" %% "gestalt-utils" % "0.0.1-SNAPSHOT" withSources()

resolvers ++= Seq(
		"scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
		"snapshots" at "http://scala-tools.org/repo-snapshots",
		"releases"  at "http://scala-tools.org/repo-releases",
        "gestalt-snapshots" at "https://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local",
        "gestalt-releases" at "https://galacticfog.artifactoryonline.com/galacticfog/libs-releases-local"
		)
