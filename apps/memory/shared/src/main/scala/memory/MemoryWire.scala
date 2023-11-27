package memory

import scala.util.{Failure, Success, Try}

import cs214.webapp.*
import cs214.webapp.wires.*
import cs214.webapp.exceptions.DecodingException

object MemoryWire extends AppWire[MemoryEvent, MemoryView]:
  import MemoryEvent.*
  import MemoryView.*
  import ujson.*

  override object eventFormat extends WireFormat[MemoryEvent]:
    override def encode(event: MemoryEvent): Value =
      ???

    override def decode(js: Value): Try[MemoryEvent] =
      ???

  override object viewFormat extends WireFormat[MemoryView]:

    override def encode(v: MemoryView): Value =
      ???
    override def decode(js: Value): Try[MemoryView] =
      ???

