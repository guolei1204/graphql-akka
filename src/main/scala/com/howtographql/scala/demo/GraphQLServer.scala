package com.howtographql.scala.demo

import akka.http.scaladsl.server.{Route, _}
import sangria.parser.QueryParser
import spray.json.{JsObject, JsString, JsValue}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}
import sangria.ast.Document
import sangria.execution.{ExceptionHandler => EHandler, _}
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import com.howtographql.scala.demo.models.{AuthenticationException, AuthorizationException}
import sangria.marshalling.sprayJson._

object GraphQLServer {
  private val dao = DBSchema.createDatabase

  def endpoint(requestJSON: JsValue)(implicit ex: ExecutionContext): Route = {
    val JsObject(fields) = requestJSON
    val JsString(query) = fields("query")
    QueryParser.parse(query) match {
      case Success(queryAst) =>
        val operation = fields.get("operationName").collect {
          case JsString(op) => op
        }

        val variables = fields.get("variables") match {
          case Some(obj: JsObject) => obj
          case _ => JsObject.empty
        }

        complete(executeGraphQLQuery(queryAst, operation, variables))
      case Failure(err) =>
        complete(BadRequest, JsObject("error" -> JsString(err.getMessage)))
    }
  }

  val ErrorHandler = EHandler {
    case (_, AuthenticationException(message)) => HandledException(message)
    case (_, AuthorizationException(message)) => HandledException(message)
  }

  private def executeGraphQLQuery(query: Document,
                                  operation: Option[String],
                                  vars: JsObject)
                                 (implicit exec: ExecutionContext) = {
    Executor.execute(
      GraphQLSchema.SchemaDefinition,
      query,
      MyContext(dao),
      variables = vars,
      operationName = operation,
      deferredResolver = GraphQLSchema.Resoler,
      exceptionHandler = ErrorHandler,
      middleware = AuthMiddleware :: Nil
    ).map(OK -> _).recover {
      case error: QueryAnalysisError => BadRequest -> error.resolveError
      case error: ErrorWithResolver => InternalServerError -> error.resolveError
    }
  }


}
