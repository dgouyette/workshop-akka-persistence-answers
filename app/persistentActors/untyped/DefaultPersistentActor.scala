package persistentActors.untyped

import akka.actor.ActorSystem
import akka.persistence.{PersistentActor, SaveSnapshotSuccess, SnapshotOffer, SnapshotSelectionCriteria}
import commands.untyped.Command
import event.{Event, LookupBusImpl}
import eventHandlers.EventHandler
import states.State


trait DefaultPersistentActor[S <: State, E <: Event, C <: Command[E, S]] extends PersistentActor {
  var state: Option[S] = None

  implicit def as: ActorSystem

  val eventHandler: EventHandler[S, E]

  implicit val eventBus: LookupBusImpl

  override def receiveRecover: Receive = {
    case SnapshotOffer(_, stateOpt: Option[S]) =>
      state = stateOpt
      saveSnapshot(state)
    case e: E =>
      state = eventHandler.handle(e)(state)
      saveSnapshot(state)
  }

  override def receiveCommand: Receive = {
    case SaveSnapshotSuccess(metadata) =>
      deleteSnapshots(SnapshotSelectionCriteria(metadata.sequenceNr, metadata.timestamp -1))
    case c: C => {
      val snd = sender()
      c.validate(persistenceId, state).fold(
        error => snd ! Left(error),
        event => {
          persist(event) { _ =>
            state = eventHandler.handle(event)(state)
            saveSnapshot(state)
            snd ! Right(event)
            eventBus.publish(event)
          }
        }
      )
    }
  }
}
