package com.github.crowler.actor.worker

import akka.actor.{ActorRef, Props}
import akka.http.scaladsl.model.{FormData, HttpMethods, HttpRequest}
import akka.stream.Materializer
import org.jsoup.Jsoup

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.matching.Regex

/**
 * Актор загрузки и прасинрга страниц конкретных файлов (таких как https://www.softpedia.com/get/System/Hard-Disk-Utils/Intel-SSD-Datacenter-Tool.shtml).
 * Created by a.isachenkov on 10/28/2019.
 */
object FileCrowlerWorker {
  //Создавать акторы через обьект Props всегда лучше, т.к. они тогда корректно работают с akka clustering.
  def props(queueRef: ActorRef, resultListenerRef: ActorRef, materializer: Materializer): Props =
    Props(classOf[FileCrowlerWorker], queueRef, resultListenerRef, materializer)

  val popupRegex: Regex = "popup6_open\\(.+\\)".r
}

class FileCrowlerWorker(
                         override val queueRef: ActorRef,
                         override val resultListenerRef: ActorRef,
                         override val materializer: Materializer
                       ) extends BaseCrowlerWorker {

  /**
   * Логика обработки содержимого html страницы.
   *
   * @param uri        адрес страницы.
   * @param htmlString содержимое страницы.
   */
  override def handlePageContent(uri: String, htmlString: String): Unit = {
    val mirrorLinks = parseMirrorLinks(htmlString)
    resultListenerRef ! ProcessFilePageResult(uri, mirrorLinks)
  }

  /**
   * Логика парсинга ссылок из html разметки. Нам нужно каким-то образом из html тегов распарсить списки ссылок, в
   * данном случае я буду использовать css селекторы.
   *
   * @param htmlString строка, содержащая html разметку кталога ссылок на страницы файлов.
   * @return ссылоки на mirror link-и файлов.
   */
  def parseMirrorLinks(htmlString: String): Iterable[String] = {
    val parsedHtml = Jsoup.parse(htmlString)
    //селектор получения ссылок mirror link-и файлов
    val mirrorLinks = parsedHtml.select("div.dllinks > div > a[href]").eachAttr("href").asScala
    mirrorLinks
  }


  /**
   * Ссылки на скачку файла не являются частью страницы файла.
   * Они возвращается внутри ответа на POST запрос, отправляемый при нажатии на кнопку загрузки файла.
   * По-этому нам нужно запросить страницу файла, распарсить параметры POST запроса и запросить POPUP с mirror-ами
   * для загрузки файла.
   *
   * @param uri адрес страницы файла.
   * @return содержимое POPUP-а с mirror-ами для загрузки файла.
   */
  override def requestTaskPage(uri: String)(implicit ec: ExecutionContext, mat: Materializer): Future[PageContent] = {
    for {
      filePageHtml <- requestHtmlPage(HttpRequest(uri = uri))
      downloadPopupRequest = parseMirrorFormRequest(filePageHtml)
      downloadPopupHtml <- requestHtmlPage(downloadPopupRequest)
    } yield {
      PageContent(downloadPopupHtml)
    }
  }

  def parseMirrorFormRequest(htmlString: String): HttpRequest = {
    val parsedHtml = Jsoup.parse(htmlString)
    //найдём кнопку загрузки файла
    val dllBnt = parsedHtml.select("div#dlbtn1 a")
    //атрибут onclick содержит относительный путь запроса и форму, отправляемую при клике.
    val onclick = dllBnt.attr("onclick")
    val (relativePath, formParams) = parsePostData(onclick)
    HttpRequest(
      uri = s"https://www.softpedia.com$relativePath",
      method = HttpMethods.POST,
      entity = FormData(formParams: _*).toEntity
    )
  }

  /**
   * Атрибут onclick содержит относительный путь запроса и форму, отправляемую при клике.
   * Нам нужны параметры функции popup6_open:
   * popup6_open({'t':15,'id':44310,tk:'874b2a28f6ae6267191b3fcac185eab53ceafc8c',tsf:spjs_prog_tsf}, '/_xaja/dlinfo.php','');
   * Тут "/_xaja/dlinfo.php - относительный адрес, куда надо отправить запрос,
   * а {'t':15,'id':44310,tk:'874b2a28f6ae6267191b3fcac185eab53ceafc8c',tsf:spjs_prog_tsf} - форма, отправляемая в запросе.
   *
   *
   * @param onclick значение атрибута onclick у кнопки загрузки файла.
   * @return (относительный адрес, параметры формы).
   */
  def parsePostData(onclick: String): (String, Array[(String, String)]) = {
    val popup = FileCrowlerWorker.popupRegex.findFirstIn(onclick)
    //@todo use regex instead of substring
    val popapParamsStart = popup.get.indexOf("{")
    val popapParamsEnd = popup.get.lastIndexOf("}")
    val popupParams = popup.get.substring(popapParamsStart + 1, popapParamsEnd)
    val popupPathStart = popapParamsEnd + 4
    val popupPathEnd = popup.get.lastIndexOf(")")
    val relativePath = popup.get.substring(popupPathStart, popupPathEnd - 4)
    val formParams = popupParams.replace("'", "").split(",").map(pair => {
      val s = pair.split(":")
      (s.head.toString, s.tail.head.toString)
    })
    (relativePath, formParams)
  }
}

case class ProcessFilePageResult(uri: String, mirrorLinks: Iterable[String])
