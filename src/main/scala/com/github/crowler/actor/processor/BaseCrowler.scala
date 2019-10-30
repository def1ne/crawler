package com.github.crowler.actor.processor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.stream.Materializer
import com.github.crowler.actor.{CrowlerTask, CrowlerTasks, QueueActor}

import scala.collection.mutable

/**
 * Базовая логика краулера. СОздаёт набор воркеров для загрузки и парсинга страниц и очередь команд, которую воркеры
 * разгребают. Рассчитывает на то, что воркер никогда не "проглотит" сообщение так и не выслав результат.
 * Created by a.isachenkov on 10/29/2019.
 */
trait BaseCrowler extends Actor with ActorLogging {

  private var queueRef0: ActorRef = _
  private val awaitingLinks: mutable.Set[String] = mutable.Set.empty
  private val completedLinks: mutable.Set[String] = mutable.Set.empty

  def parallelism: Int

  def materializer: Materializer

  override def preStart(): Unit = {
    super.preStart()
    queueRef0 = context.actorOf(queueProps, queueActorName)
    (0 until parallelism).foreach(index => context.actorOf(buildWorkerProps(index), workerActorName(index)))
  }

  def newTasksReceive: Receive = {
    case CrowlerTask(link) =>
      addTasks(List(link)) //@todo create addLink method

    case CrowlerTasks(links) =>
      addTasks(links)
  }

  def addTasks(links: Iterable[String]): Unit = {
    log.debug("new pages {} is added", links)
    val newLinks = links.filterNot(link => awaitingLinks.contains(link) || completedLinks.contains(link))
    awaitingLinks.addAll(newLinks)
    queueRef ! CrowlerTasks(newLinks)
  }

  /**
   * Изначально я не продумал критерий остановки и не делал разбиения на awaiting и completed,
   * пока что будет вызов хука, если не осталось "ожидающих" тасков.
   */
  def completeTask(link: String): Unit = {
    log.debug("page {} is completed", link)
    awaitingLinks.remove(link)
    completedLinks.add(link)
    if (awaitingLinks.isEmpty) {
      handleAllTasksCompletion()
    }
  }

  final def queueRef: ActorRef = queueRef0

  def queueActorName: String

  def queueProps: Props = QueueActor.props()

  def workerActorName(index: Int): String

  def buildWorkerProps(index: Int): Props

  def handleAllTasksCompletion(): Unit

  def allTasksCompleted: Boolean = awaitingLinks.isEmpty
}
