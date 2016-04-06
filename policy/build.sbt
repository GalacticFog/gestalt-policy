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

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
