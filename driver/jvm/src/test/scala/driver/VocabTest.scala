package driver

import cs214.webapp.*
import cs214.webapp.messages.Action
import vocab.*

class VocabTest extends WebappTest[VocabEvent, VocabState, VocabView]:
  val sm = vocab.VocabStateMachine
  val EVENT1 = VocabEvent.NewCard("mug", "tasse")
  val EVENT2 = VocabEvent.NewCard("glass", "verre")

  private val USER_IDS = Seq(UID0, UID1, UID2)

  /** Projects a given state for each given player
    */
  def projectPlayingViews(userIds: Seq[UserId])(state: VocabState) =
    USER_IDS
      .map(uid => sm.project(state)(uid))


  def stateWithAFewCards(state: VocabState): VocabState =
    val newState = assertSingleRender:
      sm.transition(state)(UID0, EVENT1)
    assertSingleRender:
      sm.transition(newState)(UID1, EVENT2)

  def moveThroughAFewCards(state: VocabState): VocabState =
    val next1 = assertSingleRender:
      sm.transition(state)(UID0, VocabEvent.NextCard)
    
    assertSingleRender:
      sm.transition(next1)(UID0, VocabEvent.NextCard)

/// # Unit tests

/// ## Initial state

  lazy val initState = sm.init(USER_IDS)

  test("Vocab: Initial Card is blank card face up"):
    val views = projectPlayingViews(USER_IDS)(initState)
    
    for view <- views do
      assertEquals(view.card, Card("", ""))
      assertEquals(view.wordUp, true)

  test("Vocab: Initial state has correct initial player(1pts)"):
    val views = projectPlayingViews(USER_IDS)(initState)

    for playingView <- views do
      assertEquals(playingView.curPlayer, UID0)

/// ## Playing state

  test("Vocab: Playing state should let the player enter a new word (4pts)"):
    val newState = assertSingleRender:
      sm.transition(initState)(UID0, EVENT1)

    assertEquals(newState.cards(0), Card("mug", "tasse"))

  test("Vocab: State should forbid out of turn player from entering new word"):
    val newState = assertSingleRender:
      sm.transition(initState)(UID0, EVENT1)
    assertFailure[exceptions.IllegalMoveException]:
      sm.transition(newState)(UID0, EVENT1)

  test("Vocab: Flip card should flip that player's current card"):
    val newState = assertSingleRender:
      sm.transition(initState)(UID0, EVENT1)
    val stateFlipped = assertSingleRender:
      sm.transition(newState)(UID0, VocabEvent.FlipCard)

    val views = projectPlayingViews(USER_IDS)(stateFlipped)
    
    for (view, index) <- views.zipWithIndex do  
      if index != 0 then assertEquals(view.wordUp, true)
      else assertEquals(view.wordUp, false)

  test("Vocab: Next card after last card should circle back around to first card"):
    val s1 = stateWithAFewCards(initState)
    val s2 = moveThroughAFewCards(s1)
    assertEquals(s2.cards(s2.playerCard(UID0)), vocab.Card("mug", "tasse"))

  test("Vocab: Current player should be able to pass turn to let friend add word"):
    val s1 = assertSingleRender:
      sm.transition(initState)(UID0, VocabEvent.PassTurn)
    
    assertEquals(s1.players(s1.curPlayer), UID1)



/// ## Encoding and decoding

  test("Vocab: Event wire"):
    EVENT1.testEventWire
    VocabEvent.FlipCard.testEventWire
    VocabEvent.NextCard.testEventWire
    VocabEvent.PassTurn.testEventWire

  test("Vocab: View wire"):
    for
      move <- List(EVENT1, VocabEvent.FlipCard, VocabEvent.NextCard, VocabEvent.PassTurn)
      u <- USER_IDS
    do
      val aState = assertSingleRender:
        sm.transition(initState)(UID0, move)
      sm.project(aState)(u).testViewWire
