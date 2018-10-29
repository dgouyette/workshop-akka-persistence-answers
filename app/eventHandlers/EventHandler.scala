package eventHandlers

import event.{BoardEvent, Event, TicketEvent}
import states.{Board, Ticket}

trait EventHandler[State, E <: Event] {
  def handle(e: E)(s: Option[State]): Option[State]
}

object BoardEventHandler extends EventHandler[states.Board, event.BoardEvent] {
  override def handle(e: BoardEvent)(state: Option[Board]): Option[Board] = {
    (state, e) match {
      case (None, event.BoardEvent.Created(_,title, description)) => Some(Board(title, description))
      case (Some(b), event.BoardEvent.StatusChanged(newStatus)) => Some(b.copy(status = newStatus))
    }
  }
}

object TicketEventHandler extends EventHandler[states.Ticket, event.TicketEvent] {

  override def handle(e: TicketEvent)(state: Option[states.Ticket]): Option[Ticket] = {
    (state, e) match {
      case (None, TicketEvent.Created(_, boardId, title, description,_)) => Some(Ticket(Ticket.Todo, boardId, title, description))
      case (Some(t), TicketEvent.StatusChanged(_,newStatus)) => Some(t.copy(status = newStatus))
    }
  }
}