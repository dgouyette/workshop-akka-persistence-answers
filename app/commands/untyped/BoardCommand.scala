package commands.untyped

import cats.data.Validated
import event.BoardEvent
import event.BoardEvent.StatusChanged
import states.Board
import states.Board.Archived

sealed trait BoardCommand extends Command[BoardEvent, Board]

object BoardCommand {

  case class Create(title: String, description: String) extends BoardCommand {
    override def validate(id: String, persisted: Option[Board]): Validated[Command.Error, BoardEvent] = {
      (persisted, title, description) match {
        case (None, _, _) if description.isEmpty || title.isEmpty => Validated.invalid("description or title must not be empty")
        case (Some(_), _, _) => Validated.invalid("board already exists")
        case _ => Validated.valid(BoardEvent.Created(id, title, description))
      }
    }
  }

  case object Archive extends BoardCommand {
    override def validate(id: String, persisted: Option[Board]): Validated[Command.Error, BoardEvent] = {
      persisted
        .map(_ => Validated.valid(StatusChanged(Archived)))
        .getOrElse(Command.BoardNotFound)
    }
  }

}