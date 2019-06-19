name := "lamport-clocks"
organization := "com.gregbarasch"
version := "1.0"

scalaVersion := "2.12.7"
scalacOptions ++= Seq(
  "-deprecation"
  ,"-unchecked"
  ,"-encoding", "UTF-8"
  ,"-Xlint"
  ,"-Xverify"
  ,"-feature"
  ,"-language:postfixOps"
)

val akka = "2.5.21"
val lightbend = "1.0.1"

libraryDependencies ++= Seq (
  "com.typesafe.akka" %% "akka-actor" % akka
  ,"com.typesafe.akka" %% "akka-cluster" % akka
  ,"com.typesafe.akka" %% "akka-cluster-metrics" % akka
  ,"com.typesafe.akka" %% "akka-discovery" % akka
  ,"com.lightbend.akka.discovery" %% "akka-discovery-kubernetes-api" % lightbend
  ,"com.lightbend.akka.management" %% "akka-management-cluster-bootstrap" % lightbend
  ,"com.lightbend.akka.management" %% "akka-management-cluster-http" % lightbend
  ,"log4j" % "log4j" % "1.2.17"
)

mainClass in Compile := Some("com.gregbarasch.lamportclocks.App")
enablePlugins(JavaAppPackaging)

maintainer := "Greg Barasch <gregbarasch@gmail.com>"
// dockerRepository := Some("gregbarasch")
dockerBaseImage := "openjdk:11"
dockerExposedPorts ++= Seq(8080, 8558, 2552)
enablePlugins(DockerPlugin)
