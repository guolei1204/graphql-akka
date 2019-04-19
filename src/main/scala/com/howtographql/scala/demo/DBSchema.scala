package com.howtographql.scala.demo

import akka.http.scaladsl.model.DateTime
import slick.jdbc.H2Profile.api._
import java.sql.Timestamp

import com.howtographql.scala.demo.models.{Link, User, Vote}

import scala.concurrent.duration._
import scala.concurrent.Await
import scala.language.postfixOps


object DBSchema {

  implicit val dateTimeColumnType = MappedColumnType.base[DateTime, Timestamp](
    dt => new Timestamp(dt.clicks),
    ts => DateTime(ts.getTime)
  );

  val links = TableQuery[LinksTable]

  val users = TableQuery[UsersTable]

  val votes = TableQuery[VotesTable]
  /**
    * Load schema and populate sample data withing this Sequence od DBActions
    */
  val databaseSetup = DBIO.seq(
    users.schema.create,
    users.forceInsertAll(Seq(
      User(1, "xiaoxiao", "xiaoxiao@qq.com", "323232"),
      User(2, "dada", "dada@qq.com", "111111")
    )),

    links.schema.create,
    links.forceInsertAll(Seq(
      Link(1, "http://howtographql.com", "Awesome community driven GraphQL tutorial", 1, DateTime(2019, 1, 2)),
      Link(2, "http://graphql.org", "Official GraphQL web page", 1, DateTime(2018, 2, 9)),
      Link(3, "https://facebook.github.io/graphql/", "GraphQL specification", 2, DateTime(2012, 9, 3))
    )),

    votes.schema.create,
    votes.forceInsertAll(Seq(
      Vote(id = 1, userId = 1, linkId = 1),
      Vote(id = 2, userId = 1, linkId = 2),
      Vote(id = 3, userId = 2, linkId = 3),
      Vote(id = 4, userId = 2, linkId = 1),
    ))
  )


  def createDatabase: DAO = {
    val db = Database.forConfig("h2mem")

    Await.result(db.run(databaseSetup), 10 seconds)

    new DAO(db)

  }

  class UsersTable(tag: Tag) extends Table[User](tag, "USERS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)
    def name = column[String]("NAME")
    def email = column[String]("EMAIL")
    def password = column[String]("PASSWORD")
    def createdAt = column[DateTime]("CREATED_AT")

    def * = (id, name, email, password, createdAt).mapTo[User]
  }


  class LinksTable(tag: Tag) extends Table[Link](tag, "LINKS") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def url = column[String]("URL")

    def description = column[String]("DESCRIPTIONS")

    def postBy = column[Int]("USER_ID")

    def createdAt = column[DateTime]("CREATED_AT")

    def * = (id, url, description, postBy, createdAt).mapTo[Link]

    def postedByFK = foreignKey("postBy_FK", postBy, users)(_.id)
  }




  class VotesTable(tag: Tag) extends Table[Vote](tag, "VOTES") {
    def id = column[Int]("ID", O.PrimaryKey, O.AutoInc)

    def userId = column[Int]("USERID")

    def linkId = column[Int]("LINKID")

    def createdAt = column[DateTime]("CREATED_AT")

    def * = (id, userId, linkId, createdAt).mapTo[Vote]

    def userFk = foreignKey("user_fk", userId, users)(_.id)

    def linkFk = foreignKey("link_fk", linkId, links)(_.id)
  }

}
