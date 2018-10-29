package serialization

import akka.persistence.pg.JsonString
import play.api.libs.json.{JsObject, Json}
import states.{Board, Ticket}

class StateJsonEncoder extends akka.persistence.pg.event.JsonEncoder {

  override def toJson: PartialFunction[Any, JsonString] = {
    case value : Some[_] =>   toJson(value.get)
    case p: Board => JsonString((Json.toJson(p).as[JsObject] ++ Json.obj("type" -> "Board")).toString())
    case p: Ticket => JsonString((Json.toJson(p).as[JsObject] ++ Json.obj("type" -> "Ticket")).toString())
    case None => JsonString(s"""{"type" : "None"}""")
    case other => JsonString(s"""{"type" : "${other.toString}" }""")
  }

  override def fromJson: PartialFunction[(JsonString, Class[_]), AnyRef] = {
    case (json, _) =>
      val value = Json.parse(json.value)
      val jsType = (value \ "type").as[String]
      jsType match {
        case "Board" => value.asOpt[Board]
        case "Ticket" => value.asOpt[Ticket]
        case _ => None
      }
  }
}
