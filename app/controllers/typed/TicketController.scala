package controllers.typed

import java.util.UUID

import akka.actor.Scheduler
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import akka.{actor => untyped}
import commands.typed.TicketCommand
import commands.untyped.Command
import controllers.CreateTicketRequest
import dao.typed.TicketDao
import event.TicketEvent
import persistentActors.typed.TicketPersistenceActor
import play.api.Configuration
import play.api.db.Database
import play.api.libs.json.{JsObject, Json}
import play.api.libs.parsers.DefaultBP
import play.api.mvc.{AbstractController, ControllerComponents, Result, WebSocket}
import serialization.EventJsonEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class TicketController(cc: ControllerComponents,
                       val parsers: DefaultBP,
                       val configuration: Configuration,
                       val ticketDao: ActorRef[TicketDao.Protocol],
                       val eventJsonEncoder: EventJsonEncoder
                      )(implicit actorSystem: untyped.ActorSystem, database: Database) extends AbstractController(cc) {
  implicit val scheduler: Scheduler = actorSystem.scheduler
  implicit val timeout: Timeout = Timeout(10 seconds)

  import akka.actor.typed.scaladsl.adapter._

  def commandErrorToResult: Command.Error => Result = (err: Command.Error) => BadRequest(err)

  def ticketPersistentActor(ticketId: String): ActorRef[TicketCommand] = actorSystem.spawnAnonymous(TicketPersistenceActor.behavior(ticketId))

  def list(boardId: String) = Action.async {
    import akka.actor.typed.scaladsl.AskPattern._
    for {
      tickets <- ticketDao ? ((ref: ActorRef[Seq[JsObject]]) => TicketDao.FindAllByBoardId(boardId, ref))
    } yield Ok(Json.toJson(tickets))
  }

  def updateStatusTodo(boardId: String, ticketId: String) = Action.async {
      for {
        res <- ticketPersistentActor(ticketId) ? ((ref: ActorRef[Either[Command.Error, TicketEvent]]) => TicketCommand.UpdateStatusTodo(ref))
      } yield res.fold(error => BadRequest(error), _ => Ok)
  }

  def updateStatusInProgress(boardId: String, ticketId: String) = Action.async {
      for {
        res <- ticketPersistentActor(ticketId) ? ((ref: ActorRef[Either[Command.Error, TicketEvent]]) => TicketCommand.UpdateStatusInProgress(ref))
      } yield res.fold(error => BadRequest(error), _ => Ok)
  }


  def updateStatusDone(boardId: String, ticketId: String) = Action.async {
      for {
        res <- ticketPersistentActor(ticketId) ? ((ref: ActorRef[Either[Command.Error, TicketEvent]]) => TicketCommand.UpdateStatusDone(ref))
      } yield res.fold(error => BadRequest(error), _ => Ok)
  }


  def create(boardId: String) = Action.async(parse.json(CreateTicketRequest.r)) {
    implicit req =>
      val newId = UUID.randomUUID().toString
      for {
        res <- ticketPersistentActor(newId) ? ((ref: ActorRef[Either[Command.Error, TicketEvent]]) => TicketCommand.Create(boardId, req.body.title, req.body.description, ref))
      } yield res.fold(
        err => commandErrorToResult(err),
        event => Created(Json.toJson(event))
      )
  }

  def show = TODO

  lazy val readJournal = PersistenceQuery(actorSystem).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  def events(boardId: String): WebSocket = WebSocket.accept[String, String] { _ =>
    val eventSource = readJournal.eventsByTag("TicketEvent", 0L)
      .map(_.event)
      .collect { case e: TicketEvent if e.boardId == boardId => eventJsonEncoder.toJson(e).value }
    Flow.fromSinkAndSource(Sink.ignore, eventSource)
  }
}
