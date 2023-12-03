package memory

import scala.util.{Try, Random}

import ujson.Value

import cs214.webapp.*
import cs214.webapp.messages.*
import cs214.webapp.exceptions.*
import cs214.webapp.server.WebServer

import memory.*

// Feel free to tweak this value!
private val SHOW_CARDS_PAUSE_MS = 2500

object MemoryStateMachine extends cs214.webapp.StateMachine[MemoryEvent, MemoryState, MemoryView]:

  val name: String = "memory"
  val wire = MemoryWire

  def Deck(cards: String): Vector[String] =
    cards.trim.split(" +").to(Vector)

  val DECKS: Map[String, Vector[String]] = Map(
    "Simple" -> Deck("""
      💫 ⭐️
    """),
    "Stars" -> Deck("""
      💫 ⭐️ 🌟 ✨ ☀️
    """),
    "Animals" -> Deck("""
      🐵 🐒 🦍 🦧 🐶 🐕 🦮 🐕‍🦺
      🐩 🐺 🦊 🦝 🐱 🐈 🐈‍⬛ 🦁
      🐯 🐅 🐆 🐴 🫎 🫏 🐎 🦄
      🦓 🦌 🦬 🐮 🐂 🐃 🐄 🐷
      🐖 🐗 🐽 🐏 🐑 🐐 🐪 🐫
      🦙 🦒 🐘 🦣 🦏 🦛 🐭 🐁
      🐀 🐹 🐰 🐇 🐿️ 🦫 🦔 🦇
      🐻 🐻‍❄️ 🐨 🐼 🦥 🦦 🦨 🦘
      🦡
    """),
    "Birds" -> Deck("""
      🦃 🐔 🐓 🐣 🐤 🐥 🐦 🐧
      🕊️ 🦅 🦆 🦢 🦉 🦤 🪶 🦩
      🦚 🦜 🪽 🐦‍⬛ 🪿
    """),
    "Marine & Reptiles" -> Deck("""
      🐸 🐊 🐢 🦎 🐍 🐲 🐉 🦕
      🦖 🐳 🐋 🐬 🦭 🐟 🐠 🐡
      🦈 🐙 🐚 🪸 🪼 🦀 🦞 🦐
      🦑 🦪
    """),
    "Bugs" -> Deck("""
      🐌 🦋 🐛 🐜 🐝 🪲 🐞 🦗
      🪳 🕷️ 🕸️ 🦂 🦟 🪰 🪱 🦠
    """),
    "Plants" -> Deck("""
      💐 🌸 💮 🪷 🏵️ 🌹 🥀 🌺
      🌻 🌼 🌷 🪻 🌱 🪴 🌲 🌳
      🌴 🌵 🌾 🌿 ☘️ 🍀 🍁 🍂
      🍃 🍄 🪨 🪵
    """)
  )

  // Use any strings you want here — the tests don't check for these specific emoji
  val CARDS: Vector[String] = DECKS("Birds")

  /** Creates a new application state. */
  override def init(clients: Seq[UserId]): MemoryState =
    ???

  override def transition(state: MemoryState)(userId: UserId, event: MemoryEvent): Try[Seq[Action[MemoryState]]] =
    ???

  override def project(state: MemoryState)(userId: UserId): MemoryView =
    ???

// Server registration magic
class register:
  WebServer.register(MemoryStateMachine)
