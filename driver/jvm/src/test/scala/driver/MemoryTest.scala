package driver

import cs214.webapp.*
import memory.*

class MemoryTest extends WebappTest[MemoryEvent, MemoryState, MemoryView]:
  val sm = memory.MemoryStateMachine

  private val USER_IDS = Seq(UID0, UID1, UID2)

  /** Coming soon! */
