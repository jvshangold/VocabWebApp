package driver

import cs214.webapp.*
import cs214.webapp.messages.Action
import memory.*

class MemoryTest extends WebappTest[MemoryEvent, MemoryState, MemoryView]:
  val sm = memory.MemoryStateMachine

  private val USER_IDS = Seq(UID0, UID1, UID2)

  val RNG = util.Random(0)

  /** Projects a given state for each given player and extract the [[stateView]]
    * field of the result.
    */
  def projectPlayingViews(userIds: Seq[UserId])(state: MemoryState) =
    USER_IDS
      .map(sm.project(state))
      .map(_.stateView.assertInstanceOf[StateView.Playing])

  /** Projects a given state for each given player and extracts the
    * [[alreadyMatched]] scores table.
    */
  def projectAlreadyMatched(userIds: Seq[UserId])(state: MemoryState) =
    userIds
      .map(sm.project(state))
      .map(_.alreadyMatched)

  case class FlipResult(
      idx1: Int,
      idx2: Int,
      card1: Card,
      card2: Card,
      nCards: Int,
      selections: (MemoryState, MemoryState),
      actions: Seq[Action[MemoryState]]
  ):
    def isMatch = card1 == card2

    def cards = (card1, card2)

    def stateWithCardsShown =
      assert(actions.nonEmpty)
      actions.head.assertInstanceOf[messages.Action.Render[MemoryState]].st

    def stateWithCardsHidden =
      assert(actions.nonEmpty)
      actions.last.assertInstanceOf[messages.Action.Render[MemoryState]].st

    def allStates =
      selections._1 +: selections._2 +: actions.collect { case Action.Render(st) => st }

  /** Makes a user play two cards and returns the cards that were selected and
    * whether they were a match or not.
    */
  def flipTwoCards(state: MemoryState, userId: UserId, cards: (Int, Int)): FlipResult =
    val firstSelection = assertSingleRender:
      sm.transition(state)(userId, MemoryEvent.Toggle(cards._1))
    val secondSelection = assertSingleRender:
      sm.transition(firstSelection)(userId, MemoryEvent.Toggle(cards._2))
    val flipActions = assertSuccess:
      sm.transition(secondSelection)(userId, MemoryEvent.FlipSelected)

    assert(flipActions.nonEmpty)

    val finalState = flipActions.head.asInstanceOf[messages.Action.Render[MemoryState]].st
    val playingStateView = sm.project(finalState)(userId)
      .stateView.assertInstanceOf[StateView.Playing]

    FlipResult(
      idx1 = cards._1,
      idx2 = cards._2,
      card1 = playingStateView.board(cards._1).assertInstanceOf[CardView.FaceUp].card,
      card2 = playingStateView.board(cards._2).assertInstanceOf[CardView.FaceUp].card,
      nCards = playingStateView.board.size,
      selections = (firstSelection, secondSelection),
      actions = flipActions
    )

  def boardSize(state: MemoryState): Int =
    sm.project(state)(UID0).stateView.assertInstanceOf[StateView.Playing].board.size

  /* Cheat by looking at all the cards to facilitate testing. */
  def guessCards(state: MemoryState): Seq[Card] =
    val nCards = boardSize(state)
    assert(nCards > 1)
    (0 until nCards).map: idx =>
      flipTwoCards(state, UID0, (idx, (idx + 1) % nCards)).card1

  /* Group indices by card. */
  def findPairs(state: MemoryState): Seq[(Int, Int)] =
    val cards = guessCards(state)
    for
      (card, indices) <- (0 until cards.length).groupBy(cards).toSeq
      _ = assert(indices.length % 2 == 0, "There should be an even number of each card.")
      pair <- indices.grouped(2)
    yield (pair(0), pair(1))

/// # Unit tests

/// ## Initial state

  lazy val initState = sm.init(USER_IDS)

  test("Memory: Initial state has all cards face down (2pts)"):
    val views = projectPlayingViews(USER_IDS)(initState)

    for view <- views do
      assertEquals(view.board.size, MemoryStateMachine.CARDS.size * 2)
      for card <- view.board do
        assertEquals(card, CardView.FaceDown)

  test("Memory: Initial state has all players at score 0 (2pts)"):
    val scores = projectAlreadyMatched(USER_IDS)(initState)

    for matchedCards <- scores do
      assertEquals(matchedCards.keys.toSet, USER_IDS.toSet)
      for userId <- USER_IDS do
        assertEquals(matchedCards(userId).size, 0)

  test("Memory: Initial state has correct initial player right number of cards (1pts)"):
    val views = projectPlayingViews(USER_IDS)(initState)

    for playingView <- views do
      assertEquals(playingView.currentPlayer, UID0)
      // There must be an even number of cards to win, and we need 4 cards to test
      assert(playingView.board.length % 2 == 0 && playingView.board.length >= 4)

/// ## Playing state

  test("Memory: Playing state should let the player select a card and mark it as selected (4pts)"):
    val newState = assertSingleRender:
      sm.transition(initState)(UID0, MemoryEvent.Toggle(0))

    for playingView <- projectPlayingViews(USER_IDS)(newState) do
      assertEquals(playingView.board(0), CardView.Selected)

  test("Memory: Playing state should forbid the player from selecting more than two cards (2pts)"):
    val stateWithOneSelectedCard = assertSingleRender:
      sm.transition(initState)(UID0, MemoryEvent.Toggle(0))
    val stateWithTwoSelectedCard = assertSingleRender:
      sm.transition(stateWithOneSelectedCard)(UID0, MemoryEvent.Toggle(1))
    assertFailure[exceptions.IllegalMoveException]:
      sm.transition(stateWithTwoSelectedCard)(UID0, MemoryEvent.Toggle(2))

  test("Memory: Toggling is involutive (1pt)"):
    val up = assertSingleRender:
      sm.transition(initState)(UID0, MemoryEvent.Toggle(0))
    val down = assertSingleRender:
      sm.transition(up)(UID0, MemoryEvent.Toggle(0))
    assertEquals(initState, down)

  def flipTwoRandomCards(matching: Boolean, state: MemoryState = initState, userId: UserId = UID0) =
    val pairs = RNG.shuffle(findPairs(initState))
    assert(pairs.length > 1, "There should be at least two different cards!")
    val (idx0, idx1) =
      if matching then pairs(0)
      else (pairs(0)._1, pairs(1)._1)
    flipTwoCards(state, userId, (idx0, idx1))

  test("Memory: Playing state show the two selected cards when flipped, pause, and hide the two cards (9pts)"):
    val cards = guessCards(initState)
    val afterFlip = flipTwoRandomCards(matching = false)

    // Three actions: render flipped, wait, render hidden
    assertEquals(afterFlip.actions.length, 3)

    // The cards are face up for the two players
    for StateView.Playing(phase, currentPlayer, board) <- projectPlayingViews(USER_IDS)(afterFlip.stateWithCardsShown)
    do
      assertEquals(board.size, cards.length)
      board(afterFlip.idx1).assertInstanceOf[CardView.FaceUp].card
      board(afterFlip.idx2).assertInstanceOf[CardView.FaceUp].card
      for
        idx <- 0 until board.length
        if idx != afterFlip.idx1 && idx != afterFlip.idx2
      do
        board(idx) == CardView.FaceDown

    // Check that we have a proper pause
    val Action.Pause(durationMs) = afterFlip.actions(1).assertInstanceOf[Action.Pause[MemoryState]]
    assert(durationMs > 100, "Too fast!")

    // Assert that the two cards are face down at the end
    val lastActionState = afterFlip.stateWithCardsHidden
    for StateView.Playing(phase, currentPlayer, board) <- projectPlayingViews(USER_IDS)(lastActionState) do
      board.forall(_ == CardView.FaceDown)

  test("Memory: Playing state should update the ScoresView if the cards are a match (2pts)"):
    for (userId, userIdx) <- USER_IDS.zipWithIndex do
      var st = initState
      // Skip others' turns
      for otherId <- USER_IDS.take(userIdx) do
        st = flipTwoRandomCards(matching = false, state = st, userId = otherId).stateWithCardsHidden
      // Find a match when it comes to userId's turn
      var afterFlip = flipTwoRandomCards(matching = true, state = st, userId = userId)
      for tricks <- projectAlreadyMatched(USER_IDS)(afterFlip.stateWithCardsHidden) do
        assertEquals(
          tricks,
          USER_IDS.map(_ -> Seq()).toMap + (userId -> Seq(afterFlip.card1, afterFlip.card2))
        )

  test("Memory: Playing state should leave scores unchanged if selected cards don't match (2pts)"):
    var state = initState
    for (userId, pairs) <- USER_IDS.zip(findPairs(initState).sliding(2)) do
      val afterFlip = flipTwoCards(state, userId, (pairs(0)._1, pairs(1)._1))
      state = afterFlip.stateWithCardsHidden
      for tricks <- projectAlreadyMatched(USER_IDS)(afterFlip.stateWithCardsHidden) do
        assertEquals(tricks, USER_IDS.map(_ -> Seq()).toMap)

  def playEntireGame(initState: MemoryState) =
    val pairs = RNG.shuffle(findPairs(initState))

    val firstFlip = flipTwoCards(initState, UID0, pairs.head)

    // Make the first player do all the moves
    pairs.tail.scanLeft(firstFlip) { case (flip, (idx1, idx2)) =>
      flipTwoCards(flip.stateWithCardsHidden, UID0, (idx1, idx2))
    }

  test("Memory: Playing state should first show that cards are matching before declaring a winner (2pts)"):
    val flips = playEntireGame(initState)
    val lastFlip = flips.last

    // Penultimate state should be revealing the cards
    val flipViews = projectPlayingViews(USER_IDS)(lastFlip.stateWithCardsShown)
    for flipView <- flipViews do
      assertEquals(flipView.phase, PhaseView.GoodMatch)

    // Check score
    val expected = USER_IDS.map(_ -> Seq()).toMap + (UID0 -> flips.flatMap(_.cards.toList))
    for am <- projectAlreadyMatched(USER_IDS)(lastFlip.stateWithCardsHidden) do
      assertEquals(am, expected)

    // Last state should be showing the winner
    val winViews = USER_IDS.map(sm.project(lastFlip.stateWithCardsHidden))
    for winView <- winViews do
      winView.stateView.assertInstanceOf[StateView.Finished]

  test("Memory: Playing state should let the next player play when two cards have not been correctly matched (4pts)"):
    val afterFlip = flipTwoRandomCards(matching = false)
    val currentPlayer = projectPlayingViews(USER_IDS)(afterFlip.stateWithCardsHidden)(0).currentPlayer
    assertEquals(currentPlayer, UID1)

  test("Memory: Playing state should let the same player play again when two cards have been correctly matched (4pts)"):
    val afterFlip = flipTwoRandomCards(matching = true)
    val currentPlayer = projectPlayingViews(USER_IDS)(afterFlip.stateWithCardsHidden)(0).currentPlayer
    assertEquals(currentPlayer, UID0)

/// ## Won state

  test("Memory: Won state should prevent any playing interaction by displaying an error (1pts)"):
    val lastState = playEntireGame(initState).last.stateWithCardsHidden

    for userId <- USER_IDS do
      assertFailure[exceptions.IllegalMoveException]:
        sm.transition(lastState)(userId, MemoryEvent.Toggle(0))

      assertFailure[exceptions.IllegalMoveException]:
        sm.transition(lastState)(userId, MemoryEvent.FlipSelected)

  test("Memory: Won state should contain the id of the player with the most cards won and not another one (2pts)"):
    val lastState = playEntireGame(initState).last.stateWithCardsHidden

    for
      uid <- USER_IDS
      view = sm.project(lastState)(uid).stateView.assertInstanceOf[StateView.Finished]
    do
      assertEquals(_, StateView.Finished(Set(UID0)))

/// ## Additional tests

  test("Memory: Cards should be randomized (2pts)"):
    assert((0 to 5).map(_ => guessCards(sm.init(USER_IDS))).distinct.size > 1)

  test("Memory: Flipping cards in a different order should give identical results (2pts)"):
    for matching <- List(true, false) do
      var flip1 = flipTwoRandomCards(matching)
      var flip2 = flipTwoCards(initState, UID0, (flip1.idx2, flip1.idx1))

      assertEquals(flip1.isMatch, matching)
      assertEquals(flip2.isMatch, matching)
      assertEquals(flip1.stateWithCardsHidden, flip2.stateWithCardsHidden)

  test("Memory: The number of cards should not from round to round (1pt)") {
    val nCards = boardSize(initState)
    for flip <- playEntireGame(initState) do
      assertEquals(boardSize(flip.stateWithCardsShown), nCards)
      assertEquals(boardSize(flip.stateWithCardsShown), nCards)
  }

  test("Memory: The game should work with different subsets of players (1pt)"):
    for
      n <- 1 to USER_IDS.length
      c <- USER_IDS.combinations(n)
      if c.contains(UID0)
    do
      playEntireGame(sm.init(Seq(UID0)))

/// ## Encoding and decoding

  test("Memory: Event wire (2pt)"):
    for cardId <- 0 to Short.MaxValue do
      MemoryEvent.Toggle(cardId).testEventWire
    MemoryEvent.FlipSelected.testEventWire

  test("Memory: View wire (8pts)"):
    for
      n <- 1 to USER_IDS.length
      userIds = USER_IDS.take(n)
      flip <- playEntireGame(sm.init(userIds))
      s <- flip.allStates
      u <- userIds
    do
      sm.project(s)(u).testViewWire
