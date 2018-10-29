package controllers.untyped

import java.util.UUID
import akka.{actor => untyped}
import akka.pattern.ask
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import com.softwaremill.tagging._
import commands.untyped.{Command, TicketCommand}
import controllers.CreateTicketRequest
import dao.untyped.Dao.TicketDaoTag
import dao.untyped.TicketDao
import event.{LookupBusImpl, TicketEvent}
import persistentActors.untyped.TicketPersistenceActor
import play.api.Configuration
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
                       val ticketDao: untyped.ActorRef @@ TicketDaoTag,
                       val eventJsonEncoder: EventJsonEncoder

                      )(implicit val actorSystem: untyped.ActorSystem, mat: Materializer, evenBus: LookupBusImpl) extends AbstractController(cc) {

  implicit val timeout: Timeout = Timeout(2 seconds)

  def commandErrorToResult: Command.Error => Result = (err: Command.Error) => BadRequest(err)


  def ticketPersistentActor(idTicket: String): untyped.ActorRef = actorSystem.actorOf(TicketPersistenceActor.props(idTicket))


  def list(boardId: String) = Action.async {
    for {
      tickets <- (ticketDao ?  TicketDao.FindAllByBoardId(boardId)).mapTo[List[JsObject]]
    } yield Ok(Json.toJson(tickets))
  }

  def updateStatusTodo(boardId: String, ticketId: String) = Action.async(parse.empty) {
    implicit req =>
    for {
      res <- (ticketPersistentActor(ticketId) ? TicketCommand.UpdateStatusTodo).mapTo[Either[Command.Error, TicketEvent]]
    } yield res.fold(error => BadRequest(error), _ => Ok)
  }

  def updateStatusInProgress(boardId: String, ticketId: String) = Action.async(parse.empty) {
    implicit req =>
      for {
        res <- (ticketPersistentActor(ticketId) ? TicketCommand.UpdateStatusInProgress).mapTo[Either[Command.Error, TicketEvent]]
      } yield res.fold(error => BadRequest(error), _ => Ok)
  }

  def updateStatusDone(boardId: String, ticketId: String) = Action.async(parse.empty) {
    implicit req =>
      for {
        res <- (ticketPersistentActor(ticketId) ? TicketCommand.UpdateStatusDone).mapTo[Either[Command.Error, TicketEvent]]
      } yield res.fold(error => BadRequest(error), _ => Ok)
  }


  def create(boardId: String) = Action.async(parse.json(CreateTicketRequest.r)) {
    implicit req =>
      val newId = UUID.randomUUID().toString
      for {
        res <- (ticketPersistentActor(newId) ? TicketCommand.Create(boardId, req.body.title, req.body.description)).mapTo[Either[Command.Error, TicketEvent.Created]]
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