package memory

import java.nio.file.{Files, Paths}

import scala.io.Source
import scala.util.{Random, Try}

import cs214.webapp.UserId

/** Cards are simply strings in this version of the game (use Emoji!) */
type Card = String

/** A view of the memory's state for a specific client.
  *
  * The UI alternates between two views: selecting cards (where the current
  * player picks two cards and then clicks “flip!”) while others wait for cards
  * to be selected, and viewing the results (when the cards are briefly shown).
  *
  * @param stateView
  *   A projection of the current phase of the game.
  * @param alreadyMatched
  *   The cards that each player has successfully matched since the beginning of
  *   the game.
  */
case class MemoryView(
    stateView: StateView,
    alreadyMatched: ScoresView
)

type ScoresView =
  Map[UserId, Seq[Card]]

enum StateView:
  /** The game is ongoing. */
  case Playing(phase: PhaseView, currentPlayer: UserId, board: Seq[CardView])

  /** The game is over; there may be more than one winner if two players have
    * the same score.
    */
  case Finished(winnerIds: Set[UserId])

enum PhaseView:
  /** It's our turn to pick two cards. */
  case SelectingCards

  /** We've picked two cards, time to press flip! */
  case CardsSelected

  /** It's another player's turn, so we're just waiting for them to flip cards.
    */
  case Waiting

  /** We're looking at a correct match (the board indicates what cards are
    * visible).
    */
  case GoodMatch

  /** We're looking at an incorrect match. */
  case BadMatch

enum CardView:
  case FaceDown
  case Selected
  case FaceUp(card: Card)
  case AlreadyMatched(card: Card)

enum MemoryEvent:
  /** Select or deselect a card. */
  case Toggle(cardId: Int)

  /** Flips selected cards. */
  case FlipSelected

type MemoryState = Unit // Change this!
