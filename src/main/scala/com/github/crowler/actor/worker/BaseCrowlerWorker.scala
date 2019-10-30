package com.github.crowler.actor.worker

import akka.actor.{Actor, ActorLogging, ActorRef, Stash, Status}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, StatusCode, StatusCodes}
import akka.pattern.pipe
import akka.stream.Materializer
import akka.util.ByteString
import com.github.crowler.actor.{RequestTask, CrowlerTask, TaskProcessingFailure}

import scala.concurrent.{ExecutionContext, Future}

/**
 * Базовый актор загрузки и прасинрга html страниц. Всё содержимое html страницы загружается в память для последующего
 * парсинга.
 * Created by a.isachenkov on 10/28/2019.
 */
trait BaseCrowlerWorker extends Actor with ActorLogging with Stash {

  import context.{system, dispatcher}

  def queueRef: ActorRef

  def resultListenerRef: ActorRef

  def materializer: Materializer

  override def receive: Receive = readyReceive

  override def preStart(): Unit = {
    super.preStart()
    requestNextTask()
  }

  def requestNextTask(): Unit = {
    queueRef ! RequestTask
  }

  /**
   * Состояние актора "готов к получению новой команды".
   */
  def readyReceive: Receive = {
    case CrowlerTask(uri) =>
      val currentSender = sender()
      requestTaskPage(uri)(dispatcher, materializer) pipeTo self
      context.become(handleResultReceive(uri, currentSender))
  }

  /**
   * Состояние актора "ожидаю завершения загрузки страницы".
   *
   * @param uri           адрес страницы.
   * @param requestSender актор, от которого пришёл запрос на обработку страницы.
   */
  def handleResultReceive(uri: String, requestSender: ActorRef): Receive = {
    case pageContent: PageContent =>
      handlePageContent(uri, pageContent.html)
      context.become(readyReceive)
      requestNextTask()

    case Status.Failure(exception) =>
      handlePageRequestFailure(uri, exception)
      context.become(readyReceive)
      requestNextTask()

    case _ =>
      stash()
  }

  def requestTaskPage(uri: String)(implicit ec: ExecutionContext, mat: Materializer): Future[PageContent] = {
    requestHtmlPage(HttpRequest(uri = uri)).map(PageContent.apply)
  }

  /**
   * Логика загрузки содержимого html страницы.
   *
   * @todo add throttling logic.
   * @param request запрос.
   * @return содержимое страницы.
   */
  def requestHtmlPage(request: HttpRequest)(implicit ec: ExecutionContext, mat: Materializer): Future[String] = {
    Http().singleRequest(request).flatMap(response => {
      if (response.status == StatusCodes.OK) {
        response.entity.dataBytes.runFold(ByteString.empty)(_ ++ _).map(_.utf8String)
      } else Future.failed(WrongResponseCode(response.status, StatusCodes.OK))
    })
  }

  /**
   * Логика обработки ошибки запроса html страницы.
   *
   * @param uri       адрес страницы.
   * @param exception ошибка.
   */
  def handlePageRequestFailure(uri: String, exception: Throwable): Unit = {
    resultListenerRef ! TaskProcessingFailure(uri, exception)
  }

  /**
   * Логика обработки содержимого html страницы.
   *
   * @param uri        адрес страницы.
   * @param htmlString содержимое страницы.
   */
  def handlePageContent(uri: String, htmlString: String): Unit
}

case class PageContent(html: String)
case class WrongResponseCode(code: StatusCode, expectedCode: StatusCode) extends Throwable(s"")
