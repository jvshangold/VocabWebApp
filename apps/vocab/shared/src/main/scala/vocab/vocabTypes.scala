package vocab

import java.nio.file.{Files, Paths}

import scala.io.Source
import scala.util.{Random, Try}

import cs214.webapp.UserId

/** Cards are simply strings in this version of the game (use Emoji!) */
case class Card(word: String, translation: String)

/** A view of the memory's state for a specific client.
  *
  * The UI alternates between two views: It is this player's turn so they can enter a word,
  * and this player is waiting for the other player to enter a word. Also tells the user what
  * card we are on and which side is up
  *
  * @param card
  *   User current card
  * @param wordUp
  *   Which side of the card is up. 'wordUp' meaning translation is down
  * @param curInput
  *   The current input in the input fields for a new word
  * @param myTurn
  *   Whose turn it is. If it's our turn we have a bar to enter words. If not, no bar/
  *   bar is not accessible
  */
case class VocabView(
    card: Card, wordUp: Boolean, myTurn: Boolean, curPlayer: String, entering: Boolean
)

enum VocabEvent:
  /** enter a new word */
  case NewCard(word: String, translation: String)

  /** Flip the current card. keeps current input. */
  case FlipCard

  /** Go to the Next Card. keeps current input. */
  case NextCard

  /** pass turn */
  case PassTurn

  /** Play with the words you and your friend have entered. */
  case PlayWithWords

  /** Enter more words. */
  case EnterWords

case class VocabState(players: Vector[UserId], playerCard: Map[UserId, Int], 
  curPlayer: Int, playerCardUp: Map[UserId, Boolean], cards: Vector[Card], entering: Boolean)
