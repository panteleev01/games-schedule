package repository

import cats.Id
import cats.effect.{Clock, IO}
import cats.implicits.catsSyntaxEitherId
import config.HttpClientConfig
import domain.Decoders.gameDayDecoder
import domain.Domain.{AppError, GameSeries, InternalAppError, RequestError}
import org.http4s.circe.jsonOf
import org.http4s.client.Client
import org.http4s.headers.Accept
import org.http4s.{EntityDecoder, HttpVersion, MediaType, Request, Response}
import repository.Util.renderUrl
import tofu.logging.Logging
import tofu.logging.Logging.Make

import java.time.Instant
import java.time.temporal.ChronoUnit

trait NHLClient {
  def getTodaysGames: IO[Either[AppError, GameSeries]]

  def getTomorrowsGames: IO[Either[AppError, GameSeries]]
}

object NHLClient {

  private final class Impl(httpClient: Client[IO], config: HttpClientConfig)(implicit clock: Clock[IO])
    extends NHLClient {

    private val request: Request[IO] = Request[IO](
      httpVersion = HttpVersion.`HTTP/2`
    ).putHeaders(Accept(MediaType.application.json))

    private def errorHandler(response: Response[IO]): IO[Throwable] = {
      response
        .attemptAs(EntityDecoder.text)
        .foldF(
          failure => IO(RequestError(s"Could not decode response error: ${failure.message}")),
          str => IO(RequestError(s"Error while sending request: $str"))
        )
    }

    override def getTodaysGames: IO[Either[AppError, GameSeries]] = for {
      instant <- clock.realTimeInstant
      games <- getGamesOnDate(instant)
    } yield games

    override def getTomorrowsGames: IO[Either[AppError, GameSeries]] = for {
      instant <- clock.realTimeInstant
      tomorrow = instant.plus(1, ChronoUnit.DAYS)
      games <- getGamesOnDate(tomorrow)
    } yield games

    private def getGamesOnDate(instant: Instant): IO[Either[AppError, GameSeries]] =
      renderUrl(config.baseUrl, instant).flatMap {
        case Left(error) => IO(InternalAppError(error).asLeft)
        case Right(uri) =>
          httpClient
            .expectOr(
              request.withUri(uri)
            )(errorHandler)(jsonOf[IO, GameSeries])
            .attempt
            .map {
              case Right(gameDay) => gameDay.asRight
              case Left(error)    => InternalAppError(error).asLeft
            }

      }
  }

  private final class LoggingImpl(repository: NHLClient)(implicit
    logging: Logging[IO]
  ) extends NHLClient {

    override def getTodaysGames: IO[Either[AppError, GameSeries]] =
      Util.surroundWithLogs(repository.getTodaysGames)("Getting games for today")(err =>
        (s"Error while getting games for today", err.cause)
      )(games => s"Got all games: $games")

    override def getTomorrowsGames: IO[Either[AppError, GameSeries]] =
      Util.surroundWithLogs(repository.getTomorrowsGames)("Getting games for tomorrow")(err =>
        (s"Error while getting games for tomorrows", err.cause)
      )(games => s"Got all games: $games")
  }

  def make(
    httpClient: Client[IO],
    config: HttpClientConfig
  ): NHLClient = {
    val logs: Make[IO] = Logging.Make.plain[IO]
    implicit val logging: Id[Logging[IO]] = logs.forService[GameSQL]
    val impl = new Impl(httpClient, config)
    new LoggingImpl(impl)
  }
}
