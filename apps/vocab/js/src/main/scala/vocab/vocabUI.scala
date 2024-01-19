package vocab

import scala.scalajs.js.annotation.{JSExportAll, JSExportTopLevel}

import org.scalajs.dom
import scalatags.JsDom.all.*

import cs214.webapp.*
import cs214.webapp.client.*
import vocab.*

object VocabClientApp extends WSClientApp:
  def name: String = "vocab"

  def init(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element): ClientAppInstance =
    VocabClientAppInstance(userId, sendMessage, target)

class VocabClientAppInstance(userId: UserId, sendMessage: ujson.Value => Unit, target: dom.Element)
    extends StateMachineClientAppInstance[VocabEvent, VocabView](userId, sendMessage, target):

  def name: String = "vocab"

  override val wire: AppWire[VocabEvent, VocabView] = VocabWire

  override def render(userId: UserId, view: VocabView): Frag =
    frag(
      h2(b("Vocab: "), "Test your vocab with your friends!"),
      if view.entering then h2("Enter some words!") else h2("Play with your words!"),
      renderView(view)
    )

  private def eventCurried(vocEvent: String, myTurn: Boolean, entering: Boolean)(e: dom.Event) =
    e.preventDefault();
    val word = if myTurn && entering then dom.document.querySelector("input[name=word]").asInstanceOf[dom.html.Input].value else ""
    val translation = if myTurn && entering then dom.document.querySelector("input[name=translation]").asInstanceOf[dom.html.Input].value else ""
    vocEvent match
      case "NewCard" => sendEvent(VocabEvent.NewCard(word, translation))
      case "FlipCard" => sendEvent(VocabEvent.FlipCard)
      case "NextCard" => sendEvent(VocabEvent.NextCard)
      case "PlayWithWords" => sendEvent(VocabEvent.PlayWithWords)
      case "EnterWords" => sendEvent(VocabEvent.EnterWords)
      case "SkipTurn" => sendEvent(VocabEvent.PassTurn)
 
  def renderView(view: VocabView): Frag = 
    val VocabView(card, wordUp, myTurn, curPlayer, entering) = view
      frag((myTurn match
        case true =>
          if entering then
            p(
              form(
                onsubmit := eventCurried("NewCard", myTurn, entering),      
                input(`type` := "text", attr("name") := "word", placeholder := "word", required := true),
                input(`type` := "text", attr("name") := "translation", placeholder := "translation", required := true),
                input(`type` := "submit", value := "Submit new word!"),
                input(`type` := "submit", value := "Skip turn", onclick := eventCurried("SkipTurn", myTurn, entering)),
                input(`type` := "submit", value := "Play with your words!", onclick := eventCurried("PlayWithWords", myTurn, entering))
              )
            )
          else frag()
        case false => frag()),
      renderCard(view.card, view.wordUp),
      if !entering then 
        div(
          form(
            input(`type` := "submit", value := "Flip", onclick := eventCurried("FlipCard", myTurn, entering)),
            input(`type` := "submit", value := "Next card", onclick := eventCurried("NextCard", myTurn, entering)),
            input(`type` := "submit", value := "Enter more words!", onclick := eventCurried("EnterWords", myTurn, entering)))
        )
      else frag())

  def renderCard(card: Card, wordUp: Boolean) =
      val Card(word, translation) = card
      p(
        cls := "card",
        if wordUp then word else translation
      )

// Scala.js magic to register our application from this file
@JSExportAll
object VocabRegistration:
  @JSExportTopLevel("VocabExport")
  val registration = WebClient.register(VocabClientApp)
