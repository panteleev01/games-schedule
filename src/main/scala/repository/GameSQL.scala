package repository

import cats.implicits.{catsSyntaxEitherId, toFunctorOps, toTraverseOps}
import cats.syntax.applicative._
import domain.Domain._
import doobie.implicits.toSqlInterpolator
import doobie.util.update.Update0
import doobie.{ConnectionIO, Query0, Update}

trait GameSQL {
  def markGame(userId: UserId, gameId: GameId): ConnectionIO[Either[AppError, Unit]]

  def getMarkedGamesForUser(userId: UserId): ConnectionIO[Either[GameNotFound, List[Game]]]

  def getGame(gameId: GameId): ConnectionIO[Either[GameNotFound, Game]]

  def saveGameDay(gameDay: GameSeries): ConnectionIO[Unit]

  def unmarkGame(userId: UserId, gameId: GameId): ConnectionIO[Either[GameNotFound, Unit]]
}

object GameSQL {
  object sqls {

    def markGameSql(userId: UserId, gameId: GameId): doobie.Update0 =
      sql"""
            insert into stared (user_id, game_id) values (${userId.id}, ${gameId.id})
      """.update

    def getStaredGamesForUserSql(userId: UserId): Query0[StaredGame] =
      sql"select user_id, game_id from stared where user_id = ${userId.id}".query[StaredGame]

    def unmarkGameSql(userId: UserId, gameId: GameId): Update0 =
      sql"delete from stared where game_id = ${gameId.id} and user_id = ${userId.id}".update

    def getTeamSql(teamId: TeamId): Query0[Team] =
      sql"select team_id, name from teams where team_id = ${teamId.id}".query[Team]

    def getGameInfoSql(gameId: GameId): Query0[(TeamId, TeamId, GameDate)] =
      sql"select home_id, away_id, game_date from games where game_id = ${gameId.id}".query[(TeamId, TeamId, GameDate)]

    val multipleInsertSql: String =
      "insert into games (game_id, home_id, away_id, game_date) values (?, ?, ?, ?) on conflict do nothing"

    def getStaredGameSql(userId: UserId, gameId: GameId): Query0[StaredGame] =
      sql"select user_id, game_id from stared where user_id = ${userId.id} and game_id = ${gameId.id}".query[StaredGame]
  }

  private final class Impl extends GameSQL {

    import sqls._

    private def upcast[A, B >: A](a: A): B = a

    override def markGame(userId: UserId, gameId: GameId): ConnectionIO[Either[AppError, Unit]] =
      getGameInfoSql(gameId).option.flatMap {
        case None => upcast[GameNotFound, AppError](GameNotFound()).asLeft[Unit].pure[ConnectionIO]
        case Some(_) =>
          getStaredGameSql(userId, gameId).option.flatMap {
            case Some(_) => upcast[GameAlreadyExists, AppError](GameAlreadyExists()).asLeft[Unit].pure[ConnectionIO]
            case None    => markGameSql(userId, gameId).run.void.map(_.asRight)
          }
      }

    override def getMarkedGamesForUser(userId: UserId): ConnectionIO[Either[GameNotFound, List[Game]]] = for {
      staredGames <- getStaredGamesForUserSql(userId).to[List]
      games <- staredGames.map(g => getGame(g.gameId)).sequence
    } yield games.sequence

    def getGame(gameId: GameId): ConnectionIO[Either[GameNotFound, Game]] =
      getGameInfoSql(gameId).option.flatMap {
        case None => GameNotFound().asLeft[Game].pure[ConnectionIO]
        case Some((homeId, awayId, date)) => {
          val x = for {
            homeTeam <- getTeamSql(homeId).unique
            awayTeam <- getTeamSql(awayId).unique
          } yield Game(gameId, homeTeam, awayTeam, date)
          x.map(_.asRight[GameNotFound])
        }
      }

    override def saveGameDay(gameDay: GameSeries): ConnectionIO[Unit] = {
      val tuples =
        gameDay.list.map(g => (g.gameId.id, g.home.teamId.id, g.away.teamId.id, g.gameDate.date.toEpochMilli))
      Update[(Long, Long, Long, Long)](multipleInsertSql).updateMany(tuples).void
    }

    override def unmarkGame(userId: UserId, gameId: GameId): ConnectionIO[Either[GameNotFound, Unit]] =
      getStaredGameSql(userId, gameId).option.flatMap {
        case Some(_) => unmarkGameSql(userId, gameId).run.void.map(_.asRight)
        case None    => GameNotFound().asLeft[Unit].pure[ConnectionIO]
      }

  }

  def make: GameSQL = new Impl
}
