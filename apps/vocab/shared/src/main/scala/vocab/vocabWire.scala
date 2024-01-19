package vocab

import scala.util.{Failure, Success, Try}

import cs214.webapp.*
import cs214.webapp.wires.*
import cs214.webapp.exceptions.DecodingException

object VocabWire extends AppWire[VocabEvent, VocabView]:
  import VocabEvent.*
  import VocabView.*
  import ujson.*

  override object eventFormat extends WireFormat[VocabEvent]:
    override def encode(event: VocabEvent): Value =
      event match
        case NewCard(word, translation) => ujson.Arr(ujson.Str(word), ujson.Str(translation))
        case FlipCard => ujson.Str("FlipCard")
        case NextCard => ujson.Str("NextCard")
        case PassTurn => ujson.Str("PassTurn")
        case PlayWithWords => ujson.Str("PlayWithWords")
        case EnterWords => ujson.Str("EnterWords") 

    override def decode(js: Value): Try[VocabEvent] =
      Try {
        js match
          case ujson.Arr(buffer) => NewCard(buffer(0).str, buffer(1).str)
          case ujson.Str(event) => event match
            case "PassTurn" => PassTurn
            case "PlayWithWords" => PlayWithWords
            case "EnterWords" => EnterWords
            case "FlipCard" => FlipCard
            case "NextCard" => NextCard
          case _ => throw new IllegalArgumentException("Don't know what this is lol")
      }
      

  override object viewFormat extends WireFormat[VocabView]:

    override def encode(v: VocabView): Value =
      v match
        case VocabView(card, wordUp, myTurn, curPlayer, entering) => 
          val Card(word, translation) = card
          ujson.Arr(ujson.Arr(ujson.Str(word), ujson.Str(translation)), ujson.Bool(wordUp), 
          ujson.Bool(myTurn), ujson.Str(curPlayer), ujson.Bool(entering))
      
    override def decode(js: Value): Try[VocabView] =
      Try {
        val arr = js.arr
        val card = js(0).arr
        VocabView(Card(card(0).str, card(1).str), arr(1).bool, arr(2).bool, arr(3).str, arr(4).bool)
      }

