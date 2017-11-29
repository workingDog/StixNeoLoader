organization := "com.github.workingDog"

name := "stixneoloader"

version := (version in ThisBuild).value

scalaVersion := "2.12.4"

crossScalaVersions := Seq("2.12.4")

libraryDependencies ++= Seq(
  "org.neo4j.driver" % "neo4j-java-driver" % "1.4.5",
  "com.typesafe" % "config" % "1.3.2",
  "com.typesafe.play" %% "play-json" % "2.6.7",
  "com.github.workingDog" %% "scalastix" % "0.5",
  "org.slf4j" % "slf4j-nop" % "1.7.25"
)

homepage := Some(url("https://github.com/workingDog/StixNeoLoader"))

licenses := Seq("Apache 2" -> url("http://www.apache.org/licenses/LICENSE-2.0"))

assemblyMergeStrategy in assembly := {
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".dsa" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".sf" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last.toLowerCase endsWith ".des" => MergeStrategy.discard
  case PathList(xs @_*) if xs.last endsWith "LICENSES.txt"=> MergeStrategy.discard
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName in assembly := "stixneoloader-1.0.jar"

mainClass in assembly := Some("com.kodekutters.StixNeoLoader")

mainClass in(Compile, run) := Some("com.kodekutters.StixNeoLoader")
