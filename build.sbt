name := "akka.streams.kafka"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.13.10"
libraryDependencies ++= {
  val akkaVersion = "2.7.0"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % "2.1.1",
    "ch.qos.logback" % "logback-classic" % "1.4.3",
    "org.scalatest" %% "scalatest" % "3.2.14" % Test
  )
}
