package io.github.morgaroth.gdrivesync.parallel.actors

import akka.actor.{Actor, ActorRef, Props}
import com.typesafe.config.Config
import io.github.morgaroth.gdrivesync.parallel.models.Loggers
import net.ceedubs.ficus.Ficus._

import scala.collection.mutable

object Master {
  def props(cfg: Config) = Props(classOf[Master], cfg)

  case object Dispatch

}

class Master(cfg: Config) extends Actor {

  val workerCnt = cfg.as[Option[Int]]("workers").getOrElse(10)
  val loggers = Loggers.fromConfig(cfg, context.system)
  val syncToServer = cfg.as[Option[Boolean]]("sync-to-server").getOrElse(false)
  var freeWorkers = List.fill(workerCnt)(context actorOf Worker.props(cfg, loggers, syncToServer))
  var pending = List.empty[SyncFile]
  val busy = mutable.Set.empty[ActorRef]

  loggers.infos.info(s"Running sync with $workerCnt workers and sync to server flag $syncToServer")

  override def receive: Receive = {
    case sf: SyncFile =>
      pending ++= List(sf)
      dispatch()
    case more: List[SyncFile] =>
      pending ++= more
      dispatch()
    case Done =>
      busy -= sender()
      freeWorkers = sender() :: freeWorkers
      dispatch()
      checkEnd()
  }

  def dispatch() {
    (freeWorkers, pending) match {
      case (worker :: rest, task :: left) =>
        worker ! task
        busy += worker
        freeWorkers = rest
        pending = left
        dispatch()
      case _ =>
    }
  }

  def checkEnd() {
    if (freeWorkers.length == workerCnt && busy.isEmpty && pending.isEmpty) {
      loggers.progress.info("Synchronizing done. Existing...")
      context.children.foreach(context.stop)
      context.system.shutdown()
    }
  }
}