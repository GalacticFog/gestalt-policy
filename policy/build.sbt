name := """gestalt-policy"""

version := "0.0.1-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws
)

libraryDependencies += "com.rabbitmq" % "amqp-client" % "3.6.1"

libraryDependencies += "com.galacticfog" %% "gestalt-lambda-io" % "0.0.1-SNAPSHOT" withSources()

libraryDependencies += "com.galacticfog" %% "gestalt-meta-sdk-scala" % "0.1.1-SNAPSHOT" withSources()

libraryDependencies += "com.galacticfog" %% "gestalt-utils" % "0.0.1-SNAPSHOT" withSources()

resolvers ++= Seq(
		"scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
		"snapshots" at "http://scala-tools.org/repo-snapshots",
		"releases"  at "http://scala-tools.org/repo-releases",
		"gestalt" at "http://galacticfog.artifactoryonline.com/galacticfog/libs-snapshots-local"
		)
