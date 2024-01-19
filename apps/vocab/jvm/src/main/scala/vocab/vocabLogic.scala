package vocab

import scala.util.{Try, Random}

import ujson.Value

import cs214.webapp.*
import cs214.webapp.messages.*
import cs214.webapp.exceptions.*
import cs214.webapp.server.WebServer

import vocab.*

object VocabStateMachine extends cs214.webapp.StateMachine[VocabEvent, VocabState, VocabView]:
  val name: String = "vocab"
  val wire = VocabWire
  /** Creates a new application state. */
  override def init(clients: Seq[UserId]): VocabState =
    VocabState(clients.toVector, clients.zip(Vector.fill(clients.size)(0)).toMap, 0, clients.zip(Vector.fill(clients.size)(true)).toMap, Vector(Card("", "")), true)

  override def transition(state: VocabState)(userId: UserId, event: VocabEvent): Try[Seq[Action[VocabState]]] =
    import VocabEvent.* 
    import Action.*
    Try {
      val myTurn = state.players(state.curPlayer) == userId
      event match
        case NewCard(word, translation) =>
          if !myTurn then
            throw IllegalMoveException("Not your turn")
          else
            val card = Card(word, translation)
            val cards = if state.cards(0) == Card("", "") then Vector(card) else state.cards.appended(card)
            Seq(Render(VocabState(state.players, state.playerCard, 
            (state.curPlayer + 1) % state.players.size, state.playerCardUp, cards, state.entering)))
        case FlipCard =>
          val cardUp = state.playerCardUp(userId)
          Seq(Render(VocabState(state.players, state.playerCard,
          state.curPlayer, state.playerCardUp.updated(userId, !cardUp), state.cards, state.entering)))
        case NextCard =>
          val curCard = state.playerCard(userId)
          Seq(Render(VocabState(state.players, state.playerCard.updated(userId, (curCard + 1) % state.cards.size),
          state.curPlayer, state.playerCardUp, state.cards, state.entering)))
        case PassTurn => 
          Seq(Render(state.copy(curPlayer = (state.curPlayer + 1) % state.players.size)))
        case PlayWithWords =>
          Seq(Render(state.copy(entering = false)))
        case EnterWords =>
          Seq(Render(state.copy(entering = true)))
    }

  override def project(state: VocabState)(userId: UserId): VocabView =
    val entering = state.entering
    val curPlayer = state.players(state.curPlayer)
    val myTurn = curPlayer == userId
    val card = state.cards(state.playerCard(userId))
    val wordUp = state.playerCardUp(userId)
    VocabView(card, wordUp, myTurn, curPlayer, entering)


// Server registration magic
class register:
  WebServer.register(VocabStateMachine)
