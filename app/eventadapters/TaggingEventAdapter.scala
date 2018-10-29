package eventadapters

import akka.persistence.journal.{Tagged, WriteEventAdapter}
import event.{BoardEvent, TicketEvent}

class TaggingEventAdapter extends WriteEventAdapter {
  override def manifest(event: Any): String = ""

  def withTag(event: Any, tag: String) = Tagged(event, Set(tag))

  override def toJournal(event: Any): Any = event match {
    case _: BoardEvent => withTag(event, BoardEvent.getClass.getSimpleName.replace("$", ""))
    case _: TicketEvent => withTag(event, TicketEvent.getClass.getSimpleName.replace("$", ""))
  }
}