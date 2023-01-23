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
    "com.typesafe.akka" %% "akka-stream-kafka" % "4.0.0",
    "ch.qos.logback" % "logback-classic" % "1.4.5",
    "org.scalatest" %% "scalatest" % "3.2.15" % Test
  )
}
