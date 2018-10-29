package commands.untyped

import cats.data.Validated
import event.TicketEvent
import states.Ticket
import states.Ticket.{Done, InProgress, Todo}

sealed trait TicketCommand extends Command[TicketEvent, states.Ticket]

object TicketCommand {

  case object UpdateStatusTodo extends TicketCommand {
    override def validate(id: String, persisted: Option[Ticket]): Validated[Command.Error, TicketEvent] = {
      persisted match {
        case Some(Ticket(Todo, _, _, _)) => Validated.invalid("wrong status")
        case Some(Ticket(_, boardId, _, _)) => Validated.valid(TicketEvent.StatusChanged(boardId, Todo))
        case None => Command.TicketNotFound
      }
    }
  }

  case object UpdateStatusInProgress extends TicketCommand {
    override def validate(id: String, persisted: Option[Ticket]): Validated[Command.Error, TicketEvent] = {
      println(s"id = $id persisted = $persisted + this"+this)
      persisted match {
        case Some(Ticket(InProgress, _, _, _)) => Validated.invalid("wrong status")
        case Some(Ticket(_, boardId, _, _)) => Validated.valid(TicketEvent.StatusChanged(boardId, InProgress))
        case None => Command.TicketNotFound
      }
    }
  }

  case object UpdateStatusDone extends TicketCommand {
    override def validate(id: String, persisted: Option[Ticket]): Validated[Command.Error, TicketEvent] = {
      persisted match {
        case Some(Ticket(Done, _, _, _)) => Validated.invalid("wrong status")
        case Some(Ticket(_, boardId, _, _)) => Validated.valid(TicketEvent.StatusChanged(boardId, Done))
        case None => Command.TicketNotFound
      }
    }
  }


  case class Create(boardId: String, title: String, description: String) extends TicketCommand {
    override def validate(id: String, persisted: Option[states.Ticket]): Validated[Command.Error, TicketEvent] = {
      (persisted, title, description) match {
        case (None, _, _) if description.isEmpty || title.isEmpty => Validated.invalid("description or title must not be empty")
        case (Some(_), _, _) => Validated.invalid("ticket already exists")
        case _ => Validated.valid(TicketEvent.Created(id, boardId, title, description))
      }
    }
  }

}