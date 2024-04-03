import sbt._

object Dependencies {

  val http4sVersion = "0.23.23"
  val circeVersion = "0.14.6"
  val doobieVersion = "1.0.0-RC1"
  val newTypeVersion = "0.4.4"
  val telegramiumVersion = "8.69.0"
  val slf4jVersion = "2.0.5"
  val tofuVersion = "0.12.0.1"

  val deps = Seq(
    "org.http4s" %% "http4s-dsl" % http4sVersion,
    "org.http4s" %% "http4s-ember-server" % http4sVersion,
    "org.http4s" %% "http4s-ember-client" % http4sVersion,

    "org.typelevel" %% "cats-core" % "2.9.0",
    "org.typelevel" %% "cats-effect" % "3.5.2",

    "io.github.apimorphism" %% "telegramium-core" % telegramiumVersion,
    "io.github.apimorphism" %% "telegramium-high" % telegramiumVersion,

    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "org.slf4j" % "slf4j-simple" % slf4jVersion,

    "io.circe" %% "circe-generic" % circeVersion,
    "io.circe" %% "circe-parser" % circeVersion,

    "org.tpolecat" %% "doobie-core"     % doobieVersion,
    "org.tpolecat" %% "doobie-postgres" % doobieVersion,
    "org.tpolecat" %% "doobie-hikari"   % doobieVersion,
    "io.estatico"  %% "newtype"         % newTypeVersion,

    "tf.tofu" %% "tofu-logging" % tofuVersion,
    "tf.tofu" %% "tofu-logging-derivation" % tofuVersion,
    "tf.tofu" %% "tofu-logging-layout" % tofuVersion,
    "tf.tofu" %% "tofu-logging-logstash-logback" % tofuVersion,
    "tf.tofu" %% "tofu-logging-structured" % tofuVersion,
    "tf.tofu" %% "tofu-core-ce3" % tofuVersion,
    "tf.tofu" %% "tofu-doobie-logging-ce3" % tofuVersion,
    "com.softwaremill.sttp.client3" %% "core" % "3.8.15",
    "tf.tofu" %% "derevo-circe" % "0.13.0",
    "io.estatico" %% "newtype" % "0.4.4",
    "com.github.pureconfig" %% "pureconfig" % "0.17.4",
    "org.http4s" %% "http4s-ember-server" % "0.23.19",
    "com.softwaremill.sttp.tapir" %% "tapir-derevo" % "1.9.2",

    "com.dimafeng" %% "testcontainers-scala-postgresql" % "0.40.12",
    "org.postgresql" % "postgresql" % "42.5.4",
    "org.scalatest" %% "scalatest" % "3.2.15",
    "com.dimafeng" %% "testcontainers-scala-scalatest" % "0.40.12"
  )
}
