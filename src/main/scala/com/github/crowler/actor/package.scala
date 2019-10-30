package com.github.crowler

/**
 * Created by a.isachenkov on 10/29/2019.
 */
package object actor {
  case class CrowlerTask(uri: String)
  case class CrowlerTasks(uri: Iterable[String])
  case class DownloadTask(rootURI: String, mirrors: Iterable[String])
  case class TaskProcessingFailure(uri: String, reason: Throwable)

  case class NoMirrorsFile(rootURI: String)

  case object CollectionCrowlerTasksCompleted
  case object FileCrowlerTasksCompleted

  case object RequestTask
}
