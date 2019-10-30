package com.github.crowler.actor.result

import java.io.{BufferedWriter, File, FileWriter}

import akka.actor.{Actor, ActorLogging, Props}
import com.github.crowler.actor.{DownloadTask, FileCrowlerTasksCompleted, NoMirrorsFile, TaskProcessingFailure}

/**
 * Запись результатов в файл с подсчётом полученных команд.
 *
 * @todo add json-like serialization
 *       Created by a.isachenkov on 10/30/2019.
 */
object FileCrowlerResultListener {
  val actorName: String = "file-crowler-result-listener"

  def props(resultFilePath: String): Props = Props(classOf[FileCrowlerResultListener], resultFilePath: String)
}

class FileCrowlerResultListener(resultFilePath: String) extends Actor with ActorLogging {
  var counter: Int = 0
  var writer: BufferedWriter = _

  override def preStart(): Unit = {
    super.preStart()
    val outputFile = new File(resultFilePath)
    outputFile.getParentFile.mkdirs()
    writer = new BufferedWriter(new FileWriter(outputFile))
  }

  override def postStop(): Unit = {
    writer.close()
    super.postStop()
  }

  override def receive: Receive = {
    case DownloadTask(rootURI, mirrors) =>
      counter += 1
      val taskBuffer = new StringBuffer(rootURI).append(" ---- ")
      mirrors.headOption.foreach(head => mirrors.tail.foldLeft(taskBuffer.append(head))(_.append(", ").append(_)))
      writer.write(taskBuffer.append("\n").toString)
      writer.flush()

    case NoMirrorsFile(rootURI) =>
      counter += 1
      writer.write(s"$rootURI ---- no mirrors found\n")
      writer.flush()

    case TaskProcessingFailure(rootURI, reason) =>
      counter += 1
      writer.write(s"$rootURI ---- ${reason.getClass.getSimpleName} - ${reason.getMessage}\n")
      writer.flush()

    case FileCrowlerTasksCompleted =>
      log.debug("all tasks are processed, total files: {}", counter)
      context.stop(self)
      context.system.terminate()
  }
}
