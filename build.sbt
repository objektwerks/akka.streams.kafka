name := "akka.streams.kafka"
organization := "objektwerks"
version := "0.1-SNAPSHOT"
scalaVersion := "3.3.0-RC3"
libraryDependencies ++= {
  val akkaVersion = "2.6.20" // Don't upgrade due to BSL 1.1!
  Seq(
    ("com.typesafe.akka" %% "akka-actor" % akkaVersion).cross(CrossVersion.for3Use2_13),
    ("com.typesafe.akka" %% "akka-stream" % akkaVersion).cross(CrossVersion.for3Use2_13),
    ("com.typesafe.akka" %% "akka-slf4j" % akkaVersion).cross(CrossVersion.for3Use2_13),
    ("com.typesafe.akka" %% "akka-stream-kafka" % "3.0.1").cross(CrossVersion.for3Use2_13),
    ("io.github.embeddedkafka" %% "embedded-kafka" % "3.4.0").cross(CrossVersion.for3Use2_13),
    "ch.qos.logback" % "logback-classic" % "1.4.6"
  )
}