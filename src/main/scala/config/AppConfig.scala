package config

import cats.effect.IO
import pureconfig.generic.semiauto._
import pureconfig.{ConfigReader, ConfigSource}

final case class AppConfig(db: DbConfig, bot: BotConfig, client: HttpClientConfig)
object AppConfig {
  implicit val reader: ConfigReader[AppConfig] = deriveReader

  def load: IO[AppConfig] =
    IO.delay(ConfigSource.default.loadOrThrow[AppConfig])
}

final case class DbConfig(
  url: String,
  driver: String,
  user: String,
  password: String
)
object DbConfig {
  implicit val reader: ConfigReader[DbConfig] = deriveReader
}

final case class BotConfig(
  token: String
)

object BotConfig {
  implicit val reader: ConfigReader[BotConfig] = deriveReader
}

final case class HttpClientConfig(
  baseUrl: String
)

object HttpClientConfig {
  implicit val reader: ConfigReader[HttpClientConfig] = deriveReader
}
