package tictactoe

import ujson.*
import scala.util.{Failure, Success, Try}

import cs214.webapp.wires.*
import cs214.webapp.exceptions.DecodingException
import cs214.webapp.{AppWire, WireFormat, UserId}

object TicTacToeWire extends AppWire[TicTacToeEvent, TicTacToeView]:
  import TicTacToeEvent.*
  import TicTacToeView.*

  override object eventFormat extends WireFormat[TicTacToeEvent]:
    override def encode(t: TicTacToeEvent): Value =
      ???

    override def decode(json: Value): Try[TicTacToeEvent] =
      ???

  override object viewFormat extends WireFormat[TicTacToeView]:

    def encode(t: TicTacToeView): Value =
      ???

    def decode(json: Value): Try[TicTacToeView] =
      ???
