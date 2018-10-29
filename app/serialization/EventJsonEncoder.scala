package serialization

import akka.persistence.pg.JsonString
import event.{BoardEvent, TicketEvent}
import play.api.libs.json.{JsObject, Json}

class EventJsonEncoder extends akka.persistence.pg.event.JsonEncoder {

  override def toJson: PartialFunction[Any, JsonString] = {
    case event: BoardEvent ⇒JsonString(Json.toJson(event).as[JsObject].toString())
    case event: TicketEvent ⇒JsonString(Json.toJson(event).as[JsObject].toString())
  }

  override def fromJson: PartialFunction[(JsonString, Class[_]), AnyRef] = {
    case (json, clazz) =>
      val jsValue = Json.parse(json.value)
      clazz.getSimpleName match {
        case "BoardEvent$"  => jsValue.as[BoardEvent]
        case "TicketEvent$"  => jsValue.as[TicketEvent]
      }
  }
}
