package com.github.crowler.actor

import akka.actor.{Actor, ActorLogging, Props, Stash}

import scala.collection.mutable

/**
 * Наивная реализация очереди обработки с гарантией доставки "at most once".
 * Akka pub-sub тут не подойдёт. В качестве альтернативы можно было бы использвать Router перед Worker-ами.
 * Created by a.isachenkov on 10/29/2019.
 */
object QueueActor {
  def props(): Props = Props(classOf[QueueActor])
}

class QueueActor extends Actor with ActorLogging with Stash {

  val queue: mutable.Queue[String] = mutable.Queue[String]()

  def receive: Receive = {
    case CrowlerTasks(uris) if queue.isEmpty =>
      queue.enqueueAll(uris)
      unstashAll()

    case CrowlerTasks(uris) =>
      queue.enqueueAll(uris)

    case RequestTask if queue.isEmpty =>
      stash()

    case RequestTask =>
      sender ! CrowlerTask(queue.dequeue())
  }
}
