package commands.typed

import akka.actor.typed.ActorRef
import cats.data.Validated
import event.BoardEvent
import states.Board
import states.Board.Archived


sealed trait BoardCommand extends Command[BoardEvent, Board] {
  def validate(id : String, persisted: Option[Board]): Validated[Command.Error, BoardEvent] = ???
}

object BoardCommand {

  case class Exit(replyTo: ActorRef[Either[Command.Error, BoardEvent]]) extends BoardCommand {
    override def validate(id: String, persisted: Option[Board]): Validated[Command.Error, BoardEvent] = Validated.valid(BoardEvent.Exit)
  }

  case class Create(title: String, description: String, replyTo: ActorRef[Either[Command.Error, BoardEvent]]) extends BoardCommand {
    override def validate(id: String, persisted: Option[Board]): Validated[String, BoardEvent] = {
      (persisted, title, description) match {
        case (None, _, _) if description.isEmpty || title.isEmpty => Validated.invalid("description or title must not be empty")
        case (Some(_), _, _) => Validated.invalid("board already exists")
        case _ => Validated.valid(BoardEvent.Created(id, title, description))
      }
    }
  }

  case class Archive(replyTo: ActorRef[Either[String, BoardEvent]]) extends BoardCommand {
    override def validate(id: String, persisted: Option[Board]): Validated[Command.Error, BoardEvent] = {
      persisted
        .map(_ => Validated.valid(BoardEvent.StatusChanged(Archived)))
        .getOrElse(Command.BoardNotFound)
    }
  }

}