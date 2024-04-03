import cats.effect.{ExitCode, IO, IOApp}
import config.AppConfig
import doobie.Transactor
import front.NHLBot
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.Client
import org.http4s.client.middleware.Logger
import repository.{GameSQL, GameStorage, NHLClient}
import service.NHLGamesService
import telegramium.bots.high.BotApi

object Application extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    BlazeClientBuilder[IO].resource
      .use { httpClient =>
        val http: Client[IO] = Logger(logBody = false, logHeaders = false)(httpClient)

        val ioConfig: IO[AppConfig] = AppConfig.load
        for {
          conf <- ioConfig
          transactor = Transactor.fromDriverManager[IO](
            conf.db.driver,
            conf.db.url,
            conf.db.user,
            conf.db.password
          )
          api = createBotBackend(http, conf.bot.token)
          gameRepo = GameSQL.make
          storage = GameStorage.make(gameRepo, transactor)
          repo = NHLClient.make(http, conf.client)
          service = NHLGamesService.make(storage, repo)

          echoBot = new NHLBot(service, api)
          res <- echoBot.start().as(ExitCode.Success)
        } yield res
      }
  }

  private def createBotBackend(http: Client[IO], token: String): BotApi[IO] =
    BotApi(http, baseUrl = s"https://api.telegram.org/bot$token")

}
