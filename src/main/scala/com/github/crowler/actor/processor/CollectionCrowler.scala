package com.github.crowler.actor.processor

import akka.actor.{ActorRef, Props}
import akka.stream.Materializer
import com.github.crowler.actor.worker.{CollectionCrowlerWorker, ProcessCatalogPageResult}
import com.github.crowler.actor.{CollectionCrowlerTasksCompleted, TaskProcessingFailure, CrowlerTasks}

/**
 * Created by a.isachenkov on 10/29/2019.
 */
object CollectionCrowler {
  val actorName: String = "collection-crowler"

  def props(initialTasks: List[String], parallelism: Int, filePageProcessor: ActorRef, materializer: Materializer): Props =
    Props(classOf[CollectionCrowler], initialTasks, parallelism, filePageProcessor, materializer)
}

class CollectionCrowler(
                         initialTasks: List[String],
                         override val parallelism: Int,
                         filePageProcessor: ActorRef,
                         override val materializer: Materializer
                       ) extends BaseCrowler {

  override def preStart(): Unit = {
    super.preStart()
    self ! CrowlerTasks(initialTasks)
  }

  override def queueActorName: String = "collection-crowler-queue"

  override def workerActorName(index: Int): String = s"collection-crowler-worker-$index"

  override def buildWorkerProps(index: Int): Props = CollectionCrowlerWorker.props(queueRef, self, materializer)

  override def receive: Receive = newTasksReceive.orElse({
    case ProcessCatalogPageResult(currentLink, fileLinks, catalogLinks) =>
      if(log.isDebugEnabled) {
        log.debug("page {} processing is completed, found {} files and {} pages", currentLink, fileLinks.size,
          catalogLinks.size)
      }
      addTasks(catalogLinks)
      completeTask(currentLink)
      filePageProcessor ! CrowlerTasks(fileLinks)

    case TaskProcessingFailure(currentLink, reason) =>
      log.debug("page {} processing is failed, reason {}", currentLink, reason)
      completeTask(currentLink) //@todo add failure handling
  })

  override def handleAllTasksCompletion(): Unit = {
    log.debug("all pages are processed")
    filePageProcessor ! CollectionCrowlerTasksCompleted
    context.stop(self)
  }
}
