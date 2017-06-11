organization := "com.github.workingDog"

name := "stixneoloader"

version := (version in ThisBuild).value

scalaVersion := "2.12.2"

crossScalaVersions := Seq("2.12.2")

libraryDependencies ++= Seq(
  "org.neo4j.driver" % "neo4j-java-driver" % "1.4.0-rc1",
  "com.typesafe" % "config" % "1.3.1"
)

homepage := Some(url("https://github.com/workingDog/StixNeoLoader"))

licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

mainClass in(Compile, run) := Some("com.kodekutters.StixNeoLoader")

mainClass in assembly := Some("com.kodekutters.StixNeoLoader")

assemblyJarName in assembly := "stixneoloader-1.0.jar"
