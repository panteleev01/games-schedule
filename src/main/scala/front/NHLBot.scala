package front

import cats.Parallel
import cats.effect.{Async, IO}
import domain.Domain.{Game, _}
import front.BotUtils.{answerCallBack, replyOnCallBack, reportError, send}
import service.NHLGamesService
import telegramium.bots._
import telegramium.bots.high.{Api, LongPollBot}

class NHLBot(service: NHLGamesService, bot: Api[IO])(implicit
  asyncF: Async[IO],
  parallel: Parallel[IO]
) extends LongPollBot[IO](bot) {

  private implicit val api: Api[IO] = bot

  private val TODAY = "/today"
  private val TOMORROW = "/tmrw"
  private val MARKED = "/marked"

  private val commandHandlers: Map[String, Message => IO[Unit]] = Map(
    TODAY -> todayCommand,
    TOMORROW -> tomorrowCommand,
    MARKED -> markedCommand
  )

  // commands handlers

  /** Receives messages from user and handles passed command
    * @param msg
    *   message sent by user
    */
  override def onMessage(msg: Message): IO[Unit] = {
    val commandHandler = for {
      command <- extractCommand(msg)
      handler <- commandHandlers.get(command)
    } yield handler

    commandHandler match {
      case Some(f) => f(msg)
      case None    => reportError(msg, "Unknown command")
    }
  }

  /** Handler used for "Get marked games" command
    * @param msg
    *   message sent by user
    * @return
    *   IO, which prints all marked games of user
    */
  private def markedCommand(msg: Message): IO[Unit] = msg.from match {
    case None => reportError(msg, "Bot should be user by user")
    case Some(user) => {
      val userId = UserId(user.id)
      for {
        eitherGames <- service.getMarkedGamesForUser(userId)
        response = eitherGames match {
          case Left(error) => reportError(msg, error.toString)
          case Right(games) => {
            val renderedTable = renderTableMarked(games.zipWithIndex)
            val gamesString = GameSeries.simpleGameDayShow.show(GameSeries(games))
            val response = s"Marked games: \n\n$gamesString"
            send(response, msg.chat.id, renderedTable)
          }
        }
        _ <- response
      } yield ()
    }
  }

  /** Handler used for "Get today's schedule" command
    * @param msg
    *   message sent by user
    * @return
    *   IO, which prints today's schedule
    */
  private def todayCommand(msg: Message): IO[Unit] =
    sendSchedule("Today's schedule", msg, service.getTodayGames)

  /** Handler used for "Get tomorrow's schedule" command
    * @param msg
    *   message sent by user
    * @return
    *   IO, which prints tomorrow's schedule
    */
  private def tomorrowCommand(msg: Message): IO[Unit] =
    sendSchedule("Tomorrow's schedule", msg, service.getTomorrowsGames)

  /** Method that creates IO for printing schedule
    * @param description
    *   short description of schedule to put in message
    * @param msg
    *   message sent by user
    * @param schedule
    *   IO, containing schedule for a day
    * @return
    *   IO, which prints tomorrow's schedule
    */
  private def sendSchedule(description: String, msg: Message, schedule: IO[Either[AppError, GameSeries]]): IO[Unit] =
    schedule.flatMap {
      case Left(error) => reportError(msg, error.toString)
      case Right(games) => {
        val markup = renderTableSchedule(games.list.zipWithIndex)
        val gamesString = GameSeries.simpleGameDayShow.show(games)
        val response = s"$description \n\n$gamesString"
        send(response, msg.chat.id, markup)
      }
    }

  // callback handlers

  /** Prefix, used for "mark game" callback
    */
  private val markGamePrefix = "\\mark "

  /** Prefix, used for "get info about game" callback
    */
  private val infoPrefix = "\\info "

  /** Prefix, used for "unmark game" callback
    */
  private val unmarkPrefix = "\\unmark "

  /** Maps prefix to it's handler
    */
  private val commands: Map[String, (CallbackQuery, GameId) => IO[Unit]] = Map(
    markGamePrefix -> handleMarkCommand,
    infoPrefix -> handleInfoCommand,
    unmarkPrefix -> handleUnmarkCommand
  )

  /** Common handler for all callbacks
    * @param query
    *   callback data
    */
  override def onCallbackQuery(query: CallbackQuery): IO[Unit] = {
    val result: Option[IO[Unit]] = for {
      command <- query.data
      effect <- getCommandHandler(command, query)
    } yield effect

    result.getOrElse(send("Unknown query command", query.from.id))
  }

  /** Handler user for "mark game" callback query
    * @param query
    *   callback data
    * @param gameId
    *   id of game to mark
    * @return
    *   IO, that marks the game and sends message to user
    */
  private def handleMarkCommand(query: CallbackQuery, gameId: GameId): IO[Unit] = {
    service.markGame(UserId(query.from.id), gameId).flatMap {
      case Left(error) => answerCallBack(query, Some(error.toString))
      case Right(_)    => answerCallBack(query, Some(s"marked game $gameId"))
    }
  }

  /** Handler user for "get info about game" callback query
    * @param query
    *   callback data
    * @param gameId
    *   id of game to get info about
    * @return
    *   IO, that sends a message with detailed information about the game
    */
  private def handleInfoCommand(query: CallbackQuery, gameId: GameId): IO[Unit] =
    service.getGame(gameId).flatMap {
      case Left(error) => replyOnCallBack(query, error.toString)
      case Right(game) => {
        val button = renderInfoTable(gameId)
        replyOnCallBack(query, Game.detailedShow.show(game), button)
      }
    }

  /** Handler user for "unmark game" callback query
    * @param query
    *   callback data
    * @param gameId
    *   id of game to unmark
    * @return
    *   IO, that unmarks the game and sends message
    */
  private def handleUnmarkCommand(query: CallbackQuery, gameId: GameId): IO[Unit] =
    service.unmarkGame(UserId(query.from.id), gameId).flatMap {
      case Left(error) => replyOnCallBack(query, error.toString)
      case Right(_)    => replyOnCallBack(query, "Game was unmarked successfully")
    }

  /** Extracts command from user message
    * @param msg
    *   User message
    * @return
    *   Optional command
    */
  private def extractCommand(msg: Message): Option[String] = for {
    text <- msg.text
    command <- msg.entities.collectFirst { case BotCommandMessageEntity(offset, command) =>
      text.substring(offset, offset + command)
    }
  } yield command

  /** Finds handler for command from callBackQuery
    * @param command
    *   command, extracted from callBackQuery
    * @param callbackQuery
    *   callBackQuery
    * @return
    *   Some(command) if `command` is valid command and handler was found, otherwise None
    */
  private def getCommandHandler(command: String, callbackQuery: CallbackQuery): Option[IO[Unit]] = {
    val pairIdHandler: Option[(String, (CallbackQuery, GameId) => IO[Unit])] = commands.collectFirst {
      case (prefix, handler) if command.startsWith(prefix) => (command.substring(prefix.length), handler)
    }
    for {
      (idString, handler) <- pairIdHandler
      idLong <- idString.toLongOption
      gameId = GameId(idLong)
    } yield handler(callbackQuery, gameId)
  }

  /** Function, that converts id into command, which will be sent to server
    */
  type IdToCommand = GameId => String

  /** Renders table for marked games
    * @param games
    *   list of games with indices
    * @return
    *   MarkupKeyboard with buttons, pressing button sends a "get info about this game" callback
    */
  private def renderTableMarked(games: List[(Game, Int)]): Option[InlineKeyboardMarkup] =
    renderTable(games, id => s"$infoPrefix$id")

  /** Renders table for schedule games
    * @param games
    *   list of games with indices
    * @return
    *   MarkupKeyboard with buttons, pressing button sends a "mark this command" callback
    */
  private def renderTableSchedule(games: List[(Game, Int)]): Option[InlineKeyboardMarkup] =
    renderTable(games, id => s"$markGamePrefix$id")

  /** Common method for rendering
    * @param list
    *   list of games with indices
    * @param callbackData
    *   function, used for creating callbacks
    * @return
    *   MarkupKeyboard with buttons, pressing button sends a callback
    */
  private def renderTable(list: List[(Game, Int)], callbackData: IdToCommand): Option[InlineKeyboardMarkup] =
    if (list.isEmpty) None
    else {
      val table = list
        .grouped(4)
        .toList
        .map(_.map { case (game, no) =>
          InlineKeyboardButton((no + 1).toString, callbackData = Some(callbackData(game.gameId)))
        })
      Some(InlineKeyboardMarkup(inlineKeyboard = table))
    }

  /** Renders "detailed info" table
    * @param gameId
    *   id of game
    * @return
    *   MarkupKeyboard with one button, pressing buttons sends a command to remove game from marked
    */
  private def renderInfoTable(gameId: GameId): Option[InlineKeyboardMarkup] = Some {
    val table = List(
      List(InlineKeyboardButton("Unmark game", callbackData = Some(s"\\unmark $gameId")))
    )
    InlineKeyboardMarkup(inlineKeyboard = table)
  }

}
