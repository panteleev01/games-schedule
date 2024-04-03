package front

import cats.effect.IO
import telegramium.bots.high.Api
import telegramium.bots.high.Methods.{answerCallbackQuery, sendMessage}
import telegramium.bots.high.implicits.methodOps
import telegramium.bots.{CallbackQuery, ChatIntId, InlineKeyboardMarkup, Message}

object BotUtils {

  /** Convenient method for sending message and ignoring result
    * @param msg
    *   message for sending
    * @param userId
    *   id of user to send message
    * @param markup
    *   optional markup
    * @return
    *   io, which sends given message
    */
  def send(msg: String, userId: Long, markup: Option[InlineKeyboardMarkup] = None)(implicit
    api: Api[IO]
  ): IO[Unit] =
    sendMessage(
      chatId = ChatIntId(userId),
      text = msg,
      replyMarkup = markup
    ).exec.void

  /** Convenient method for sending error messages
    * @param msg
    *   message send by user
    * @param error¬
    *   description of error
    * @return
    *   io, which sends error message to user
    */
  def reportError(msg: Message, error: String)(implicit api: Api[IO]): IO[Unit] =
    send(s"Error occurred: $error", msg.chat.id)

  /** Convenient method for answering callBack and ignoring result
    * @param query
    *   callbackQuery sent by user
    * @param msg
    *   optional message
    * @return
    *   io, which answer callBack
    */
  def answerCallBack(query: CallbackQuery, msg: Option[String])(implicit api: Api[IO]): IO[Unit] =
    answerCallbackQuery(
      callbackQueryId = query.id,
      text = msg
    ).exec.void

  /** Convenient method for answering callBack and sending message
    * @param query
    *   callbackQuery sent by user
    * @param msg
    *   message for sending to user
    * @param markup
    *   optional markup
    * @return
    *   io, which answer callBack and sends a message to user
    */
  def replyOnCallBack(query: CallbackQuery, msg: String, markup: Option[InlineKeyboardMarkup] = None)(implicit
    api: Api[IO]
  ): IO[Unit] =
    answerCallBack(query, None) *> send(msg, query.from.id, markup)

}
