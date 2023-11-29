package driver

import cs214.webapp.*
import tictactoe.*

class TicTacToeTest extends WebappTest[TicTacToeEvent, TicTacToeState, TicTacToeView]:
  val sm = TicTacToeStateMachine

  private val USER_IDS = Seq(UID0, UID1)
  private var currentAppId: String = _

  var RCs = for (r <- 0 until 3; c <- 0 until 3) yield (r, c)

  case class MoveList(moves: List[(Int, Int)], winner: Option[UserId])

  var MOVE_LISTS =
    val TIE = MoveList(List((1, 0), (0, 0), (0, 2), (0, 1), (1, 1), (1, 2), (2, 2), (2, 0), (2, 1)), None)
    val WIN0a = MoveList(List((0, 0), (0, 1), (0, 2), (1, 0), (1, 1), (1, 2), (2, 0)), Some(UID0))
    val WIN0b = MoveList(List((0, 0), (1, 0), (2, 0), (0, 1), (1, 1), (2, 1), (0, 2)), Some(UID0))
    val WIN0c = MoveList(List((0, 0), (1, 0), (1, 1), (0, 1), (2, 2)), Some(UID0))
    val WIN1a = MoveList((2, 2) +: WIN0a.moves, Some(UID1))
    val WIN1b = MoveList((1, 2) +: WIN0b.moves, Some(UID1))
    val WIN1c = MoveList((0, 2) +: WIN0c.moves, Some(UID1))
    List(
      "TIE" -> TIE,
      "WIN0a" -> WIN0a,
      "WIN0b" -> WIN0b,
      "WIN0c" -> WIN0c,
      "WIN1a" -> WIN1a,
      "WIN1b" -> WIN1b,
      "WIN1c" -> WIN1c
    )

/// # Unit tests

  lazy val s0 = sm.init(USER_IDS)

/// ## Initialization

  test("ticTacToe: Initial state (1pt)"):
    val v0 = sm.project(s0)(UID0).assertInstanceOf[TicTacToeView.Playing]
    val v1 = sm.project(s0)(UID1).assertInstanceOf[TicTacToeView.Playing]

    // Correct player
    assert(v0.yourTurn)
    assert(!v1.yourTurn)

    for v <- List(v0, v1) do
      for (r, c) <- RCs do
        assert(v.board(r, c).isEmpty)

/// ## Playing

  test("ticTacToe: Initial move (2pts)"):
    for (r, c) <- RCs do
      val s1 = assertSingleRender:
        sm.transition(s0)(UID0, TicTacToeEvent.Move(r, c))
      for u1 <- USER_IDS do
        val v = sm.project(s1)(u1).assertInstanceOf[TicTacToeView.Playing]
        assertEquals(v.board(r, c), Some(UID0))

  type Checker = (TicTacToeEvent, TicTacToeState, Map[(Int, Int), Option[UserId]]) => Unit

  def playMoves(s0: TicTacToeState, moves: List[(Int, Int)], userIds: Seq[UserId] = USER_IDS)(check: Checker) =
    var st = s0
    var users = userIds

    var positions: Map[(Int, Int), Option[UserId]] =
      RCs.map(uid => uid -> None).toMap

    for (r, c) <- moves do
      val event = TicTacToeEvent.Move(r, c)
      val results = sm.transition(st)(users.head, event)
      // println(f"transition($st) → $results")
      st = assertSingleRender(results)
      positions = positions + ((r, c) -> Some(users.head))
      users = users.tail :+ users.head
      check(event, st, positions)

    st

  for (name, ml) <- MOVE_LISTS do
    test(f"ticTacToe: Consecutive moves: $name (2pt)"):
      // Not testing final state here
      playMoves(s0, ml.moves.dropRight(1)): (evt, s, expectedPositions) =>
        for
          u <- USER_IDS
          v = sm.project(s)(u).assertInstanceOf[TicTacToeView.Playing]
        do
          val positions = RCs.map(rc => rc -> v.board(rc._1, rc._2)).toMap
          if positions != expectedPositions then
            println(f">> $positions, $expectedPositions")
          assertEquals(positions, expectedPositions)

  def assumeOk: Checker = (_, _, _) => {}

  def testWires(testEvents: Boolean, testViews: Boolean) =
    for (_, ml) <- MOVE_LISTS do
      playMoves(s0, ml.moves): (evt, s, positions) =>
        if testEvents then
          evt.testEventWire
        if testViews then
          for u <- USER_IDS do
            sm.project(s)(u).testViewWire

  test("ticTacToe: Event wire (2pts)"):
    testWires(true, false)

  test("ticTacToe: View wire (8pts)"):
    testWires(false, true)

  for (name, MoveList(moves, expectedWinner)) <- MOVE_LISTS do
    test(f"ticTacToe: Winner: $name (2pts)"):
      val st = playMoves(s0, moves)(assumeOk)
      for u <- USER_IDS do
        val f = sm.project(st)(u).assertInstanceOf[TicTacToeView.Finished]
        assertEquals(f.winner, expectedWinner, f"Winner mismatch for $moves.")

  test("ticTacToe: NotYourTurn (1pt)"):
    assertFailure[exceptions.NotYourTurnException]:
      sm.transition(s0)(UID1, TicTacToeEvent.Move(1, 0))

  test("ticTacToe: Out of bounds (1pt)"):
    assertFailure[exceptions.IllegalMoveException]:
      sm.transition(s0)(UID0, TicTacToeEvent.Move(-1, 0))

  test("ticTacToe: Already occupied (1pt)"):
    val s1 = playMoves(s0, List((0, 0)))(assumeOk)
    assertFailure[exceptions.IllegalMoveException]:
      sm.transition(s1)(UID1, TicTacToeEvent.Move(0, 0))

  test("ticTacToe: Already over (1pt)"):
    val finished = playMoves(s0, MOVE_LISTS(0)._2.moves)(assumeOk)
    for uid <- USER_IDS do
      assertFailure[exceptions.IllegalMoveException]:
        sm.transition(finished)(uid, TicTacToeEvent.Move(0, 0))

  test("After completing the lab, please report how long you spent on it (1pt)"):
    assert(howManyHoursISpentOnThisLab() > 0.0)
