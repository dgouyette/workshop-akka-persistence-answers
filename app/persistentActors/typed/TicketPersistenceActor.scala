package persistentActors.typed

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import commands.typed.TicketCommand
import dao.typed.TicketDao
import event.TicketEvent
import eventHandlers.TicketEventHandler
import play.api.db.Database

object TicketPersistenceActor {

  def behavior(persistenceId: String)(implicit database : Database, actorSystem : ActorSystem): Behavior[TicketCommand] = PersistentBehaviors
    .receive[TicketCommand, TicketEvent, Option[states.Ticket]](
    persistenceId, None,
    (state, cmd)=> {
      cmd.validate(persistenceId, state ).fold(
        error => Effect.none[TicketEvent, Option[states.Ticket]].thenRun(_ => cmd.replyTo ! Left(error)),
        event => Effect.persist(event).thenRun(_ => cmd.replyTo !  Right(event))
        )
    },
    (state, event)=> TicketEventHandler.handle(event)(state)
  ).snapshotEvery(1).onSnapshot{
    case (metadata, _)=>  {
      //https://github.com/akka/akka/issues/24698 Typed Persistence: Implement deleting snapshots / events
      import akka.actor.typed.scaladsl.adapter._
      actorSystem.spawnAnonymous(TicketDao.behavior) ! TicketDao.DeleteSnapshot(persistenceId, metadata.sequenceNr-1)
    }
  }
}
