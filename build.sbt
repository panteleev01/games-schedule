ThisBuild / scalaVersion     := "2.13.11"
ThisBuild / version          := "1.0"

lazy val root = (project in file("."))
  .settings(
    name := "games-schedule"
  )

libraryDependencies ++= Dependencies.deps

dependencyOverrides += "io.circe" %% "circe-core" % "0.14.3"
dependencyOverrides += "org.tpolecat" %% "doobie-core" % Dependencies.doobieVersion

scalacOptions ++= Seq("-Ymacro-annotations")

enablePlugins(UniversalPlugin)
enablePlugins(DockerPlugin)
enablePlugins(JavaAppPackaging)

dockerExposedPorts ++= Seq(80)
dockerBaseImage := "openjdk:17-jdk-slim"
