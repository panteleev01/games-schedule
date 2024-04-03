package repository

import cats.effect.IO
import cats.implicits.catsSyntaxEitherId
import org.http4s.Uri
import tofu.logging.Logging
import tofu.syntax.logging.LoggingInterpolator

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId, ZoneOffset}
import scala.util.Try

object Util {

  private val formatter =
    DateTimeFormatter.ISO_OFFSET_DATE.withZone(ZoneId.from(ZoneOffset.UTC))

  def formatInstant(instant: Instant): IO[Either[Throwable, String]] =
    IO(formatInstantEither(instant))

  def formatInstantEither(instant: Instant): Either[Throwable, String] =
    Try(formatter.format(instant).filterNot(_ == 'Z')).toEither

  def renderUrl(baseUrl: String, instant: Instant): IO[Either[Throwable, Uri]] = formatInstant(instant).flatMap {
    case Left(error)      => IO(error.asLeft)
    case Right(formatted) => IO(Uri.fromString(s"$baseUrl/$formatted"))
  }

  def surroundWithLogs[E, Res](
    io: IO[Either[E, Res]]
  )(
    inputLog: String
  )(errorOutputLog: E => (String, Option[Throwable]))(
    successOutputLog: Res => String
  )(implicit logging: Logging[IO]): IO[Either[E, Res]] =
    info"$inputLog" *> io.flatTap {
      case Left(error) =>
        val (logString: String, throwable: Option[Throwable]) =
          errorOutputLog(error)
        throwable.fold(error"$logString")(err => errorCause"$logString" (err))
      case Right(success) => info"${successOutputLog(success)}"
    }
}
