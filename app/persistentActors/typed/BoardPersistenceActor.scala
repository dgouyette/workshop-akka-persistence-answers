package persistentActors.typed

import akka.actor.ActorSystem
import akka.actor.typed.Behavior
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import commands.typed.BoardCommand
import dao.typed.BoardDao
import event.BoardEvent
import eventHandlers.BoardEventHandler
import play.api.db.Database

object BoardPersistenceActor {

  def behavior(persistenceId: String)(implicit database : Database, actorSystem : ActorSystem): Behavior[BoardCommand] = PersistentBehaviors
    .receive[BoardCommand, BoardEvent, Option[states.Board]](
    persistenceId, None,
    (state, cmd)=> {
      cmd.validate(persistenceId, state ).fold(
        error => Effect.none[BoardEvent, Option[states.Board]].thenRun(_ => cmd.replyTo ! Left(error)),
        event => Effect.persist(event).thenRun(_ => cmd.replyTo !  Right(event))
        )
    },
    (state, event)=> BoardEventHandler.handle(event)(state)
  ).snapshotEvery(numberOfEvents = 1).onSnapshot{
    case (metadata, _)=>  {
      //https://github.com/akka/akka/issues/24698 Typed Persistence: Implement deleting snapshots / events
      import akka.actor.typed.scaladsl.adapter._
      actorSystem.spawnAnonymous(BoardDao.behavior) ! BoardDao.DeleteSnapshot(persistenceId, metadata.sequenceNr-1)
    }
  }
}
