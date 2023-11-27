package memory

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

import org.scalajs.dom
import scalatags.JsDom.all.*

import cs214.webapp.*
import cs214.webapp.client.*
import memory.*

object MemoryClientApp extends WSClientApp:
  def name: String = "memory"

  def init(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element): ClientAppInstance =
    MemoryClientAppInstance(userId, sendMessage, target)

class MemoryClientAppInstance(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element)
    extends StateMachineClientAppInstance[MemoryEvent, MemoryView](userId, sendMessage, target):
  import PhaseView.*
  import StateView.*

  def name: String = "memory"

  override val wire: AppWire[MemoryEvent, MemoryView] = MemoryWire

  override def render(userId: UserId, view: MemoryView): Frag =
    frag(
      h2(b("Memory: "), "Pick pairs of matching cards!"),
      renderView(view)
    )

  def flipSelected(e: dom.Event) =
    e.preventDefault();
    sendEvent(MemoryEvent.FlipSelected)

  def renderView(view: MemoryView): Frag = view.stateView match
    case Playing(phase, currentPlayer, board) =>
      frag(
        p(i(phase match
          case SelectingCards => "It's your turn! Pick two matching cards."
          case CardsSelected  => "It's your turn! Press flip."
          case Waiting        => f"It's $currentPlayer's turn."
          case GoodMatch      => f"Two points for $currentPlayer!"
          case BadMatch       => f"Better luck next time, $currentPlayer!"
        )),
        renderBoard(board, phase == SelectingCards || phase == CardsSelected),
        (phase match
          case CardsSelected =>
            form(
              onsubmit := flipSelected,
              input(`type` := "submit", value := "Flip!")
            )
          case _ => frag()
        ),
        renderScores(view.alreadyMatched)
      )
    case Finished(winnerIds) =>
      frag(
        p(
          cls := "finished",
          winnerIds.toSeq.sorted match
            case Seq(playerId) => f"$playerId won!"
            case _             => f"Winners: ${winnerIds.mkString(", ")}"
        ),
        renderScores(view.alreadyMatched)
      )

  private def selectCard(idx: Int) =
    sendEvent(MemoryEvent.Toggle(idx))

  def renderBoard(cards: Seq[CardView], allowClick: Boolean) =
    p(
      cls := "board",
      if allowClick then data.interactive := "interactive" else frag(),
      cards.zipWithIndex.map((c, idx) =>
        val onClick = if allowClick then Some(() => selectCard(idx)) else None
        renderCard(c, onClick)
      )
    )

  def renderCard(card: CardView, onClick: Option[() => Unit]) =
    val alreadyMatched = card.isInstanceOf[CardView.AlreadyMatched]
    div(
      cls := "card",
      if alreadyMatched then data.revealed := "revealed" else frag(),
      (if card == CardView.Selected then (data.selected := "selected")
       else frag()),
      onClick.filter(_ => !alreadyMatched).map(onclick := _).getOrElse(frag()),
      card match
        case CardView.FaceDown | CardView.Selected => "❓"
        case CardView.FaceUp(card)                 => card
        case CardView.AlreadyMatched(card)         => card
    )

  def renderScores(scores: ScoresView) =
    footer(
      h3("Scores"),
      p(
        cls := "scores",
        for
          (player, cards) <- scores.toSeq
        yield frag(
          div(cls := "playerId", player),
          div(
            cls := "tricks",
            cards.map(c => renderCard(CardView.FaceUp(c), None)),
            if cards.isEmpty then " " else frag() /* Force baseline */
          )
        )
      )
    )

// Scala.js magic to register our application from this file
@JSExportAll
object MemoryRegistration:
  @JSExportTopLevel("MemoryExport")
  val registration = WebClient.register(MemoryClientApp)
