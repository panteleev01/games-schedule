package domain

import domain.Domain._
import io.circe.{Decoder, HCursor}

object Decoders {

  implicit val teamIdDecoder: Decoder[TeamId] =
    implicitly[Decoder[Long]].map(TeamId.apply)

  implicit val teamNameDecoder: Decoder[TeamName] =
    implicitly[Decoder[String]].map(TeamName.apply)

  implicit val gameIdDecoder: Decoder[GameId] =
    implicitly[Decoder[Long]].map(GameId.apply)

  implicit val teamDecoder: Decoder[Team] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[TeamId]
      name <- c.downField("abbrev").as[TeamName]
    } yield Team(id, name)

  implicit val gameDecoder: Decoder[Game] = (c: HCursor) =>
    for {
      id <- c.downField("id").as[GameId]
      home <- c.downField("homeTeam").as[Team]
      away <- c.downField("awayTeam").as[Team]
      gameDate <- c.downField("startTimeUTC").as[GameDate]
    } yield Game(id, home, away, gameDate)

  implicit val gameDayDecoder: Decoder[GameSeries] = (c: HCursor) =>
    c.downField("gameWeek")
      .downArray
      .downField("games")
      .as[List[Game]]
      .map(GameSeries(_))
}
