package domain

import cats.Show
import cats.implicits.toShow
import derevo.circe.decoder
import derevo.derive
import doobie.Read
import io.estatico.newtype.macros.newtype
import repository.Util
import tofu.logging.derivation.loggable

import java.time.Instant

object Domain {

  @derive(loggable)
  @newtype
  case class TeamId(id: Long)

  object TeamId {
    implicit val read: Read[TeamId] = Read[Long].map(TeamId.apply)
  }

  @derive(loggable)
  @newtype
  case class TeamName(name: String)

  object TeamName {
    implicit val read: Read[TeamName] = Read[String].map(TeamName.apply)
    implicit val show: Show[TeamName] =
      _.name
  }

  @derive(loggable)
  @newtype
  case class GameId(id: Long)

  object GameId {
    implicit val read: Read[GameId] = Read[Long].map(GameId.apply)
  }

  @derive(loggable)
  @newtype
  case class UserId(id: Long)

  object UserId {
    implicit val read: Read[UserId] = Read[Long].map(UserId.apply)
  }

  @derive(loggable, decoder)
  @newtype
  case class GameDate(date: Instant)

  object GameDate {
    implicit val read: Read[GameDate] =
      Read[Long].map(n => GameDate(Instant.ofEpochMilli(n)))

    implicit val show: Show[GameDate] =
      date =>
        Util.formatInstantEither(date.date) match {
          case Left(error) => s"Unknown date: $error"
          case Right(date) => date
        }
  }

  @derive(loggable)
  final case class Team(teamId: TeamId, teamName: TeamName)

  object Team {
    implicit val show: Show[Team] =
      _.teamName.show
  }

  @derive(loggable)
  final case class Game(gameId: GameId, home: Team, away: Team, gameDate: GameDate)

  object Game {
    val simpleShow: Show[Game] =
      game => s"${game.away.show} @ ${game.home.show}"

    val detailedShow: Show[Game] =
      game => s"${game.away.show} @ ${game.home.show} at ${game.gameDate.show}"
  }

  @derive(loggable)
  case class StaredGame(userId: UserId, gameId: GameId)

  @derive(loggable)
  case class GameSeries(list: List[Game])

  object GameSeries {
    val simpleGameDayShow: Show[GameSeries] =
      gameDay => render(Game.simpleShow)(gameDay.list)

    val detailedGameDayShow: Show[GameSeries] =
      gameDay => render(Game.detailedShow)(gameDay.list)

    private def render(show: Show[Game])(list: List[Game]): String =
      if (list.isEmpty) "Games list is empty"
      else
        list.zipWithIndex
          .map { case (game, no) => s"${no + 1}. ${show.show(game)}" }
          .mkString("\n")
  }

  sealed abstract class AppError(
    val message: String,
    val cause: Option[Throwable] = None
  ) {
    override def toString: String =
      s"App error happened: $message, ${cause.map(_.getMessage).getOrElse("unknown error")}"
  }

  case class InternalAppError(
    cause0: Throwable
  ) extends AppError("Internal error", Some(cause0))

  case class GameAlreadyExists() extends AppError("Game exists") {
    override def toString: String = "Game already exists"
  }

  case class GameNotFound() extends AppError("Game not found") {
    override def toString: String = "Requested game not found"
  }

  case class RequestError(msg: String) extends Throwable {
    override def getMessage: String =
      s"Error happened while requesting data from NHL server: $msg"
  }

}
