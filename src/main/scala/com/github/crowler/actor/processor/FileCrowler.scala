package com.github.crowler.actor.processor

import akka.actor.{ActorRef, Props}
import akka.stream.Materializer
import com.github.crowler.actor.worker.{FileCrowlerWorker, ProcessFilePageResult}
import com.github.crowler.actor.{CollectionCrowlerTasksCompleted, DownloadTask, FileCrowlerTasksCompleted, NoMirrorsFile, TaskProcessingFailure}

/**
 * Created by a.isachenkov on 10/29/2019.
 */
object FileCrowler {
  val actorName: String = "file-crowler"

  def props(parallelism: Int, filePageProcessor: ActorRef, materializer: Materializer): Props =
    Props(classOf[FileCrowler], parallelism, filePageProcessor, materializer)
}

class FileCrowler(
                   override val parallelism: Int,
                   fileProcessor: ActorRef,
                   override val materializer: Materializer
                 ) extends BaseCrowler {
  var collectionTasksCompleted: Boolean = false

  override def queueActorName: String = "file-crowler-queue"

  override def workerActorName(index: Int): String = s"file-crowler-worker-$index"

  override def buildWorkerProps(index: Int): Props = FileCrowlerWorker.props(queueRef, self, materializer)

  override def receive: Receive = newTasksReceive.orElse({
    case CollectionCrowlerTasksCompleted =>
      log.debug("all pages received")
      collectionTasksCompleted = true
      if(allTasksCompleted) {
        handleAllTasksCompletionInternal()
      }

    case ProcessFilePageResult(currentLink, mirrorLinks) if mirrorLinks.nonEmpty =>
      if(log.isDebugEnabled) {
        log.debug("page {} processing is completed, found {} mirrors", currentLink, mirrorLinks.size)
      }
      completeTask(currentLink)
      fileProcessor ! DownloadTask(currentLink, mirrorLinks)

    case ProcessFilePageResult(currentLink, _) =>
      log.debug("page {} processing is completed, no mirrors found", currentLink)
      completeTask(currentLink) //@todo handle "empty mirror links" exception
      fileProcessor ! NoMirrorsFile(currentLink)

    case message@TaskProcessingFailure(currentLink, reason) =>
      log.debug("page {} processing is failed, reason {}", currentLink, reason)
      completeTask(currentLink) //@todo add failure handling
      fileProcessor ! message
  })

  override def handleAllTasksCompletion(): Unit = {
    if(collectionTasksCompleted) {
      handleAllTasksCompletionInternal()
    }
  }

  def handleAllTasksCompletionInternal(): Unit = {
    log.debug("all pages are processed")
    fileProcessor ! FileCrowlerTasksCompleted
    context.stop(self)
  }
}
