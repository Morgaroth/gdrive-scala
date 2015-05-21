name := "gdrive-scala"

version := "0.2.0"

scalaVersion := "2.11.6"

resolvers += "jboss" at "https://repository.jboss.org/nexus/content/repositories/thirdparty-releases/"

libraryDependencies ++= Seq(
  "com.google.apis" % "google-api-services-drive" % "v2-rev167-1.20.0",
  "io.github.morgaroth" %% "morgaroth-utils-base" % "1.2.5",
  "com.typesafe.akka" %% "akka-actor" % "2.3.10",
  "net.ceedubs" %% "ficus" % "1.1.2"
)

Revolver.settings

assemblyJarName in assembly := s"${name.value}-${version.value}.jar"