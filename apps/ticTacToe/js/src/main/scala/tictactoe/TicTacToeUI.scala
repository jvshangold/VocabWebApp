package tictactoe

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

import org.scalajs.dom
import scalatags.JsDom.all.*

import cs214.webapp.*
import cs214.webapp.client.*

object TicTacToeClientApp extends WSClientApp:
  def name: String = "tictactoe"

  def init(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element): ClientAppInstance =
    TicTacToeClientAppInstance(userId, sendMessage, target)

class TicTacToeClientAppInstance(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element)
    extends StateMachineClientAppInstance[TicTacToeEvent, TicTacToeView](userId, sendMessage, target):
  def name: String = "tictactoe"

  val wire = TicTacToeWire

  private def sendMove(r: Int, c: Int) =
    sendEvent(TicTacToeEvent.Move(r, c))

  def render(userId: UserId, view: TicTacToeView): Frag =
    frag(
      h2(b("Tic-tac-toe: "), "Be the first to draw the line!"),
      renderView(userId, view)
    )

  def renderView(userId: UserId, view: TicTacToeView): Frag = view match
    case TicTacToeView.Playing(board, yourTurn) =>
      div(
        p(i(if yourTurn then "It's your turn! Make your move."
        else "Wait for your opponents.")),
        // Now we create an html table with for the tic tac toe's rows and columns
        p(
          cls := "board",
          // We create 9 cells in the table
          for
            row <- 0 until 3
            col <- 0 until 3
          yield div(cls := "cell", onclick := (() => sendMove(row, col)), board(row, col).getOrElse(""))
        )
      )

    case TicTacToeView.Finished(winnerId) =>
      p(
        cls := "finished",
        winnerId match
          case None           => "It's a tie!"
          case Some(playerId) => f"$playerId has won the game!"
      )

// Scala.js magic to register our application from this file
@JSExportAll
object TicTacToeRegistration:
  @JSExportTopLevel("TicTacToeExport")
  val registration = WebClient.register(TicTacToeClientApp)
