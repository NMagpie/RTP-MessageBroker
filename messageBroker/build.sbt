ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "2.13.0"

val akkaVersion = "2.6.18"

val akkaHttpVersion = "10.2.9"

scalacOptions ++= Seq(
  "-feature",
  "-deprecation",
  "-encoding", "UTF-8",
  "-unchecked"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.lightbend.akka" %% "akka-stream-alpakka-sse" % "3.0.4",
  "org.scalameta" %% "munit" % "0.7.26" % Test,
  "org.json4s" %% "json4s-jackson" % "4.1.0-M1",
  "org.json4s" %% "json4s-native" % "4.1.0-M1"
)

lazy val root = (project in file("."))
  .settings(
    name := "project"
  )