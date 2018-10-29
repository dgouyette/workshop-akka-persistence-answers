package controllers.untyped

import java.util.UUID

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import com.softwaremill.tagging._
import commands.untyped.{BoardCommand, Command}
import controllers.CreateBoardRequest
import dao.untyped.BoardDao
import dao.untyped.Dao.BoardDaoTag
import event.{BoardEvent, LookupBusImpl}
import persistentActors.untyped.BoardPersistenceActor
import play.api.Configuration
import play.api.libs.json.{JsObject, Json}
import play.api.libs.parsers.DefaultBP
import play.api.mvc._
import serialization.EventJsonEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class BoardController(cc: ControllerComponents,
                      val parsers: DefaultBP,
                      val configuration: Configuration,
                      val boardDao: ActorRef @@ BoardDaoTag,
                      val eventJsonEncoder: EventJsonEncoder
                     )(implicit val actorSystem: ActorSystem, mat: Materializer, evenBus: LookupBusImpl) extends AbstractController(cc) {

  implicit val timeout: Timeout = Timeout(2 seconds)

  def commandErrorToResult: Command.Error => Result = (err: Command.Error) => BadRequest(err)

  def boardPersistentActor(idPack: String): ActorRef = actorSystem.actorOf(BoardPersistenceActor.props(idPack))



  def create: Action[CreateBoardRequest] = Action.async(parse.json(CreateBoardRequest.r)) {
    implicit req => {
      val newId = UUID.randomUUID().toString
      for {
        res <- (boardPersistentActor(newId) ? BoardCommand.Create(req.body.title, req.body.description)).mapTo[Either[Command.Error, BoardEvent.Created]]
      } yield res.fold(
        err => commandErrorToResult(err),
        event => Created(Json.toJson(event))
      )
    }
  }

  def show(id: String) = Action.async {
    implicit req =>
      for {
        board <- (boardDao ? BoardDao.FindById(id)).mapTo[Option[JsObject]]
      } yield board.map(b => Ok(Json.toJson(b))).getOrElse(NotFound)
  }

  def list = Action.async {
    for {
      boards <- (boardDao ? BoardDao.FindAll).mapTo[List[JsObject]]
    } yield Ok(Json.toJson(boards))
  }

  def index = Action {
    implicit req =>
      Ok(views.html.index())
  }

  lazy val readJournal = PersistenceQuery(actorSystem).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  def events: WebSocket = WebSocket.accept[String, String] { _ =>
    val eventSource = readJournal.eventsByTag("BoardEvent", 0L)
      .map(_.event)
      .collect { case e: BoardEvent => eventJsonEncoder.toJson(e).value }
    Flow.fromSinkAndSource(Sink.ignore, eventSource)
  }
}
