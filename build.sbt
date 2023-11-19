name := "akka.streams.kafka"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.13.12"
libraryDependencies ++= {
  val akkaVersion = "2.6.21" // Don't upgrade due to BUSL 1.1!
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.1", // Don't upgrade due to BUSL 1.1!
    "io.github.embeddedkafka" %% "embedded-kafka" % "3.6.0",
    "ch.qos.logback" % "logback-classic" % "1.4.11"
  )
}
