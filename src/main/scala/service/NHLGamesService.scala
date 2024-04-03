package service

import cats.effect.IO
import domain.Domain._
import repository.{GameStorage, NHLClient}
import tofu.syntax.feither._

trait NHLGamesService {
  def getTodayGames: IO[Either[AppError, GameSeries]]
  def getTomorrowsGames: IO[Either[AppError, GameSeries]]
  def markGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]]
  def getMarkedGamesForUser(userId: UserId): IO[Either[AppError, List[Game]]]
  def getGame(gameId: GameId): IO[Either[AppError, Game]]
  def saveGameDay(gameDay: GameSeries): IO[Either[AppError, Unit]]
  def unmarkGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]]
}

object NHLGamesService {
  final class Impl(gameStorage: GameStorage, nhlRepository: NHLClient) extends NHLGamesService {

    override def getTodayGames: IO[Either[AppError, GameSeries]] =
      nhlRepository.getTodaysGames.mapF { games =>
        gameStorage.saveGameDay(games).as(games)
      }

    override def getTomorrowsGames: IO[Either[AppError, GameSeries]] =
      nhlRepository.getTomorrowsGames.mapF { games =>
        gameStorage.saveGameDay(games).as(games)
      }

    override def markGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]] =
      gameStorage.markGame(userId, gameId)

    override def getMarkedGamesForUser(userId: UserId): IO[Either[AppError, List[Game]]] =
      gameStorage.getMarkedGamesForUser(userId)

    override def getGame(gameId: GameId): IO[Either[AppError, Game]] =
      gameStorage.getGame(gameId)

    override def saveGameDay(gameDay: GameSeries): IO[Either[AppError, Unit]] =
      gameStorage.saveGameDay(gameDay)

    override def unmarkGame(userId: UserId, gameId: GameId): IO[Either[AppError, Unit]] =
      gameStorage.unmarkGame(userId, gameId)
  }

  def make(gameStorage: GameStorage, nhlRepository: NHLClient): NHLGamesService =
    new Impl(gameStorage, nhlRepository)
}
