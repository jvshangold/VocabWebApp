package cs214.webapp.exceptions

/** An exception generated by an application */
class AppException(message: String) extends Exception(message)

/** The app was waiting for a different user. */
case class NotYourTurnException()
    extends AppException("It is not your turn!")

/** User attempted illegal move.
  * @param reason
  *   Why the move is illegal
  */
case class IllegalMoveException(reason: String)
    extends AppException(s"Invalid move: $reason!")

/** There was an error during the parsing of a JSON message. */
case class DecodingException(msg: String)
    extends AppException(f"Error while trying to decode JSON message: $msg.")
