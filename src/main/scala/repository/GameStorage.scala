package repository

import cats.Id
import cats.effect.IO
import cats.implicits.{catsSyntaxEitherId, toBifunctorOps}
import domain.Domain._
import doobie.implicits._
import doobie.{ConnectionIO, Transactor}
import tofu.logging.Logging
import tofu.logging.Logging.Make

trait GameStorage {

  def markGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]]

  def getMarkedGamesForUser(userId: UserId): IO[Either[AppError, List[Game]]]

  def getGame(gameId: GameId): IO[Either[AppError, Game]]

  def saveGameDay(gameDay: GameSeries): IO[Either[AppError, Unit]]

  def unmarkGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]]
}

object GameStorage {

  private final class Impl(repo: GameSQL, transactor: Transactor[IO]) extends GameStorage {

    private def run[A](connectionIO: ConnectionIO[A]): IO[Either[InternalAppError, A]] =
      connectionIO
        .transact(transactor)
        .attempt
        .map(_.leftMap(InternalAppError.apply))

    private def runEither[E <: AppError, A](connectionIO: ConnectionIO[Either[E, A]]): IO[Either[AppError, A]] =
      connectionIO
        .transact(transactor)
        .attempt
        .map {
          case Left(error)        => InternalAppError(error).asLeft
          case Right(Left(error)) => error.asLeft
          case Right(Right(v))    => v.asRight
        }

    override def markGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]] =
      runEither(repo.markGame(userId, gameId))

    override def getMarkedGamesForUser(userId: UserId): IO[Either[AppError, List[Game]]] =
      runEither[GameNotFound, List[Game]](repo.getMarkedGamesForUser(userId))

    override def getGame(gameId: GameId): IO[Either[AppError, Game]] =
      runEither[GameNotFound, Game](repo.getGame(gameId))

    override def saveGameDay(gameDay: GameSeries): IO[Either[AppError, Unit]] =
      run(repo.saveGameDay(gameDay))

    override def unmarkGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]] =
      runEither(repo.unmarkGame(userId, gameId))
  }

  private final class LoggingImpl(storage: GameStorage)(implicit
    logging: Logging[IO]
  ) extends GameStorage {

    override def markGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]] =
      Util.surroundWithLogs(storage.markGame(userId, gameId))(s"Saving game $gameId for $userId")(err =>
        (s"Error while saving stared game $gameId", err.cause)
      )(_ => s"Saving game $gameId for $userId")

    override def getMarkedGamesForUser(userId: UserId): IO[Either[AppError, List[Game]]] =
      Util.surroundWithLogs(storage.getMarkedGamesForUser(userId))("Getting all stared games for user")(err =>
        (s"Error while getting games for user: $userId ${err.message}", err.cause)
      )(success => s"Retrieved all stared games for $userId: $success")

    override def getGame(gameId: GameId): IO[Either[AppError, Game]] =
      Util.surroundWithLogs(storage.getGame(gameId))(s"Getting game with id: $gameId")(err =>
        (s"Error while getting game with id: $gameId ${err.message}", err.cause)
      )(success => s"Retrieved game with id: $gameId, $success")

    override def saveGameDay(gameDay: GameSeries): IO[Either[AppError, Unit]] =
      Util.surroundWithLogs(storage.saveGameDay(gameDay))(s"Saving gameday: $gameDay")(err =>
        (s"Error while saving game $gameDay: ${err.message}", err.cause)
      )(_ => s"Saved game day $gameDay")

    override def unmarkGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]] =
      Util.surroundWithLogs(storage.unmarkGame(userId, gameId))(s"Unmarking game: $gameId for user $userId")(err =>
        (s"Error while unmarking game $gameId ${err.message}", err.cause)
      )(_ => s"Unmakred game $gameId for user $userId")
  }

  def make(
    sql: GameSQL,
    transactor: Transactor[IO]
  ): GameStorage = {
    val logs: Make[IO] = Logging.Make.plain[IO]
    implicit val logging: Id[Logging[IO]] = logs.forService[GameSQL]
    val impl = new Impl(sql, transactor)
    new LoggingImpl(impl)
  }

}
