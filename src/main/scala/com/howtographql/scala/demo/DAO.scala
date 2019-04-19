package com.howtographql.scala.demo


import DBSchema._
import sangria.execution.deferred.{RelationIds, SimpleRelation}
import slick.jdbc.H2Profile.api._

import scala.concurrent._
import com.howtographql.scala.demo.models.{AuthProivderSignupData, Link, User, Vote}


class DAO(db: Database) {
  def allLinks = db.run(links.result)

  //  def getLink(id: Int): Future[Option[Link]] = db.run(
  //    links.filter(_.id === id).result.headOption
  //  )

  def getLinks(ids: Seq[Int]) = db.run(
    links.filter(_.id inSet ids).result
  )

  def getLinksByUserIds(ids: Seq[Int]): Future[Seq[Link]] = db.run(
    links.filter(_.postBy inSet ids).result
  )


  def getUsers(ids: Seq[Int]): Future[Seq[User]] = {
    db.run(
      users.filter(_.id inSet ids).result
    )
  }

  def getVotes(ids: Seq[Int]): Future[Seq[Vote]] = {
    db.run(
      votes.filter(_.id inSet ids).result
    )
  }

  def getVotesByRelationIds(rel: RelationIds[Vote]): Future[Seq[Vote]] = {
    db.run(
      votes.filter { vote =>
        rel.rawIds.collect({
          case (SimpleRelation("byUser"), ids: Seq[Int]) => vote.userId inSet ids
          case (SimpleRelation("byLink"), ids: Seq[Int]) => vote.linkId inSet ids
        }).foldLeft(true: Rep[Boolean])(_ || _)
      }.result
    )
  }


  def createUser(name: String, authProvider: AuthProivderSignupData): Future[User] = {
    val newUser = User(0, name, authProvider.email.email, authProvider.email.password)
    val insertAndReturnUserQuery = (users returning users.map(_.id)) into {
      (user, id) => user.copy(id = id)
    }
    db.run(
      insertAndReturnUserQuery += newUser
    )
  }

  def createLink(url: String, description: String, postBy: Int): Future[Link] = {
    val insertAndReturnLinkQuery = (links returning (links.map(_.id))) into {
      (link, id) => link.copy(id = id)
    }
    db.run(
      insertAndReturnLinkQuery += Link(0, url, description, postBy)
    )
  }

  def createVote(linkId: Int, userId: Int): Future[Vote] = {
    val insertAndReturnVoteQuery = (votes returning (votes.map(_.id))) into {
      (vote, id) => vote.copy(id = id)
    }
    db.run(
      insertAndReturnVoteQuery += Vote(0,userId,linkId)
    )
  }


  def authenticate(email:String,password:String):Future[Option[User]] = db.run(
    users.filter(u => u.email === email && u.password === password).result.headOption
  )
}
