package controllers.typed

import java.util.UUID

import akka.actor.ActorSystem
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern._
import akka.cluster.sharding.typed.scaladsl.{ClusterSharding, EntityRef, EntityTypeKey, ShardedEntity}
import akka.persistence.jdbc.query.scaladsl.JdbcReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.scaladsl.{Flow, Sink}
import akka.util.Timeout
import commands.typed
import commands.typed.BoardCommand
import commands.typed.BoardCommand.Exit
import commands.untyped.Command
import controllers.CreateBoardRequest
import dao.typed.BoardDao
import event.BoardEvent
import persistentActors.typed.BoardPersistenceActor
import play.api.Configuration
import play.api.db.Database
import play.api.libs.json.{JsObject, Json}
import play.api.libs.parsers.DefaultBP
import play.api.mvc.{AbstractController, ControllerComponents, WebSocket}
import serialization.EventJsonEncoder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class BoardController(cc: ControllerComponents,
                      val parsers: DefaultBP,
                      val configuration: Configuration,
                      val boardDao: ActorRef[BoardDao.Protocol],
                      val eventJsonEncoder: EventJsonEncoder
                     )(implicit actorSystem : ActorSystem, database: Database) extends AbstractController(cc) {

  implicit val scheduler = actorSystem.scheduler
  implicit val timeout: Timeout = Timeout(10 seconds)
  import akka.actor.typed.scaladsl.adapter._

  val sharding = ClusterSharding(actorSystem.toTyped)
  def shardedboardPersistentActor(boardId: String): EntityRef[BoardCommand] = {
    val TypeKey = EntityTypeKey[BoardCommand]("boardCommand")

    sharding.start(ShardedEntity(
      create = (_, entityId) ⇒ BoardPersistenceActor.behavior(entityId),
      typeKey = TypeKey,
      stopMessage = Exit(actorSystem.deadLetters.toTyped)))
    sharding.entityRefFor(TypeKey, boardId)
  }


  def boardPersistentActor(boardId: String): ActorRef[typed.BoardCommand] = actorSystem.spawnAnonymous(BoardPersistenceActor.behavior(boardId))

  def create  = Action.async(parse.json(CreateBoardRequest.r)) {
    implicit req =>
      val newId = UUID.randomUUID().toString
      for {
        result <- boardPersistentActor(newId) ?   ((ref: ActorRef[Either[Command.Error, BoardEvent]]) => typed.BoardCommand.Create(req.body.title, req.body.description, ref) )
      } yield result.fold(e => BadRequest(e), event => Created(Json.toJson(event)))
  }


  def show(id : String) = Action.async{
    for {
      board <- boardDao ? ((ref: ActorRef[Option[JsObject]]) => BoardDao.FindById(id, ref))
    } yield board.map(b => Ok(Json.toJson(b))).getOrElse(NotFound)
  }

  def list = Action.async {
    for {
      boards <- boardDao ? ((ref: ActorRef[Seq[JsObject]]) => BoardDao.FindAll(ref))
    } yield Ok(Json.toJson(boards))
  }

  lazy val readJournal = PersistenceQuery(actorSystem).readJournalFor[JdbcReadJournal](JdbcReadJournal.Identifier)

  def events: WebSocket = WebSocket.accept[String, String] { _ =>
    val eventSource = readJournal.eventsByTag("BoardEvent", 0L)
      .map(_.event)
      .collect { case e: BoardEvent => eventJsonEncoder.toJson(e).value }
    Flow.fromSinkAndSource(Sink.ignore, eventSource)
  }

}