package com.howtographql.scala.demo

import akka.http.scaladsl.model.DateTime
import com.howtographql.scala.demo.models._
import sangria.ast.StringValue
import sangria.execution.deferred.{DeferredResolver, Fetcher, Relation, RelationIds}
import sangria.macros.derive._
import sangria.schema.{Argument, Field, InputObjectType, IntType, InterfaceType, ListInputType, ListType, ObjectType, OptionType, ScalarType, Schema, StringType, UpdateCtx, fields}

object GraphQLSchema {


  implicit val GraphQLDateTime = ScalarType[DateTime](//1
    "DateTime", //2
    coerceOutput = (dt, _) => dt.toString, //3
    coerceInput = { //4
      case StringValue(dt, _, _) => DateTime.fromIsoDateTimeString(dt).toRight(DateTimeCoerceViolation)
      case _ => Left(DateTimeCoerceViolation)
    },
    coerceUserInput = { //5
      case s: String => DateTime.fromIsoDateTimeString(s).toRight(DateTimeCoerceViolation)
      case _ => Left(DateTimeCoerceViolation)
    }
  )

  val IdentifiableType = InterfaceType(
    "Identifiable",
    fields[Unit, Identifiable](
      Field("id", IntType, resolve = _.value.id)
    )
  )


  val linkByUserRel = Relation[Link, Int](name = "byUser", v => Seq(v.postBy))
  val voteByLinkRel = Relation[Vote, Int]("byLink", v => Seq(v.linkId))
  val voteByUserRel = Relation[Vote, Int]("byUser", v => Seq(v.userId))


  lazy val LinkType: ObjectType[Unit, Link] = deriveObjectType[Unit, Link](
    Interfaces(IdentifiableType),
    ReplaceField("createdAt", Field("createdAt", GraphQLDateTime, resolve = _.value.createdAt)),
    ReplaceField("postBy",
      Field("postedBy", UserType, resolve = c => usersFetcher.defer(c.value.postBy))
    ),
    AddFields(
      Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByLinkRel, c.value.id))
    )
  )

  lazy val UserType: ObjectType[Unit, User] = deriveObjectType[Unit, User](
    Interfaces(IdentifiableType),
    AddFields(
      Field("links", ListType(LinkType), resolve = c => linksFetcher.deferRelSeq(linkByUserRel, c.value.id)),
      Field("votes", ListType(VoteType), resolve = c => votesFetcher.deferRelSeq(voteByUserRel, c.value.id)),
    ))


  lazy val VoteType: ObjectType[Unit, Vote] = deriveObjectType[Unit, Vote](
    Interfaces(IdentifiableType),
    ExcludeFields("userId", "linkId"),
    AddFields(Field("user", UserType, resolve = c => usersFetcher.defer(c.value.userId))),
    AddFields(Field("link", LinkType, resolve = c => linksFetcher.defer(c.value.linkId)))
  )

  import sangria.marshalling.sprayJson._
  import spray.json.DefaultJsonProtocol._

  implicit val authProviderEmailFormat = jsonFormat2(AuthProviderEmail)
  implicit val authProviderSignupDataFormat = jsonFormat1(AuthProivderSignupData)

  implicit val AuthProviderEmailInputType: InputObjectType[AuthProviderEmail] = deriveInputObjectType[AuthProviderEmail](
    InputObjectTypeName("AUTH_PROVIDER_EMAIL")
  )


  lazy val AuthProviderSignupDataInputType: InputObjectType[AuthProivderSignupData] = deriveInputObjectType[AuthProivderSignupData]()


  implicit val votesFetcher = Fetcher.rel(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getVotes(ids),
    (ctx: MyContext, ids: RelationIds[Vote]) => ctx.dao.getVotesByRelationIds(ids)
  )


  implicit val usersFetcher = Fetcher(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getUsers(ids)
  )


  val linksFetcher = Fetcher.rel(
    (ctx: MyContext, ids: Seq[Int]) => ctx.dao.getLinks(ids),
    (ctx: MyContext, ids: RelationIds[Link]) => ctx.dao.getLinksByUserIds(ids(linkByUserRel))
  )

  val Resoler = DeferredResolver.fetchers(linksFetcher, usersFetcher, votesFetcher)

  private val Id = Argument("id", IntType)
  private val IDS = Argument("ids", ListInputType(IntType))


  val QueryType = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks", ListType(LinkType), resolve = c => c.ctx.dao.allLinks),
      Field("link",
        OptionType(LinkType),
        arguments = Id :: Nil,
        resolve = c => linksFetcher.deferOpt(c.arg(Id))
      ),
      Field("links",
        ListType(LinkType),
        arguments = IDS :: Nil,
        resolve = c => linksFetcher.deferSeq(c.arg(IDS))
      ),
      Field("users", ListType(UserType), arguments = IDS :: Nil, resolve = c => usersFetcher.deferSeq(c.arg(IDS))),
      Field("votes", ListType(VoteType), arguments = IDS :: Nil, resolve = c => votesFetcher.deferSeq(c.arg(IDS)))
    )
  )


  val NameArg = Argument("name", StringType)
  val AuthProviderArg = Argument("authProvider", AuthProviderSignupDataInputType)
  val UrlArg = Argument("url", StringType)
  val DescArg = Argument("description", StringType)
  val PostedByArg = Argument("postedById", IntType)
  val LinkIdArg = Argument("linkId", IntType)
  val UserIdArg = Argument("userId", IntType)
  val EmailArg = Argument("email", StringType)
  val PassWordArg = Argument("password", StringType)

  val Mutation = ObjectType(
    "Mutation",
    fields[MyContext, Unit](
      Field("createUser", UserType, arguments = NameArg :: AuthProviderArg :: Nil, resolve = c => c.ctx.dao.createUser(c.arg(NameArg), c.arg(AuthProviderArg))),
      Field("creatLink", LinkType, arguments = UrlArg :: DescArg :: PostedByArg :: Nil, resolve = c => c.ctx.dao.createLink(c.arg(UrlArg), c.arg(DescArg), c.arg(PostedByArg))),
      Field("createVote", VoteType, arguments = LinkIdArg :: UserIdArg :: Nil, resolve = c => c.ctx.dao.createVote(c.arg(LinkIdArg), c.arg(UserIdArg))),
      Field("login", UserType, arguments = EmailArg :: PassWordArg :: Nil, resolve = ctx => UpdateCtx(
        ctx.ctx.login(ctx.arg(EmailArg), ctx.arg(PassWordArg))
      ) { user => ctx.ctx.copy(currentUser = Some(user)) })
    )
  )


  val SchemaDefinition = Schema(QueryType)
}
