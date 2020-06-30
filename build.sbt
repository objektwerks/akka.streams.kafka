name := "kafka.akka.streams"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "2.12.11"
libraryDependencies ++= {
  val akkaVersion = "2.6.6"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.3",
    "ch.qos.logback" % "logback-classic" % "1.2.3",
    "org.scalatest" %% "scalatest" % "3.0.8" % Test
  )
}
