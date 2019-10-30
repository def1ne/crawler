package com.github.crowler

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.util.Timeout
import com.github.crowler.actor.CrowlerTasks
import com.github.crowler.actor.processor.{CollectionCrowler, FileCrowler}
import com.github.crowler.actor.result.FileCrowlerResultListener

/**
 * Created by a.isachenkov on 10/27/2019.
 */
object Boot extends App {
  implicit val system: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val fileCrowlerParallelism: Int = 1
  val collectionCrowlerParallelism: Int = 1
  val tasks: List[String] = List(
    "https://win.softpedia.com",
    "https://drivers.softpedia.com",
    "https://games.softpedia.com",
    "https://games.softpedia.com",
    "https://mac.softpedia.com",
    "https://webapps.softpedia.com",
    "https://mobile.softpedia.com/apk",
    "https://linux.softpedia.com",
    "https://linux.softpedia.com"
  )
  val resultFilePath: String = "./output/result.txt"
  val fileCrowlerResultListenerRef = system.actorOf(FileCrowlerResultListener.props(resultFilePath))
  val fileCrowlerRef = system.actorOf(
    FileCrowler.props(fileCrowlerParallelism, fileCrowlerResultListenerRef, materializer),
    FileCrowler.actorName
  )
  val collectionCrowlerRef = system.actorOf(
    CollectionCrowler.props(tasks, collectionCrowlerParallelism, fileCrowlerRef, materializer),
    CollectionCrowler.actorName
  )
  implicit val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)
}
