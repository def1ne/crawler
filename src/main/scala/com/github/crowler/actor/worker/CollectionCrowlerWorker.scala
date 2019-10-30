package com.github.crowler.actor.worker

import akka.actor.{ActorRef, Props}
import akka.stream.Materializer
import org.jsoup.Jsoup

import scala.jdk.CollectionConverters._

/**
 * Актор загрузки и прасинрга страниц с каталогами файлов (таких как https://win.softpedia.com, https://drivers.softpedia.com).
 * Created by a.isachenkov on 10/28/2019.
 */
object CollectionCrowlerWorker {
  //Создавать акторы через обьект Props всегда лучше, т.к. они тогда корректно работают с akka clustering.
  def props(queueRef: ActorRef, resultListenerRef: ActorRef, materializer: Materializer): Props =
    Props(classOf[CollectionCrowlerWorker], queueRef, resultListenerRef, materializer)
}

class CollectionCrowlerWorker(
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
    val (fileLinks, nextPageLinks) = parseLinks(htmlString)
    resultListenerRef ! ProcessCatalogPageResult(uri, fileLinks, nextPageLinks)
  }

  /**
   * Логика парсинга ссылок из html разметки. Нам нужно каким-то образом из html тегов распарсить списки ссылок, в
   * данном случае я буду использовать css селекторы.
   *
   * @param htmlString строка, содержащая html разметку кталога ссылок на страницы файлов.
   * @return (ссылки на страницы файлов, ссылки на следуюзщие страницы каталогов).
   */
  def parseLinks(htmlString: String): (Iterable[String], Iterable[String]) = {
    val parsedHtml = Jsoup.parse(htmlString)
    //селектор получения ссылок на страницы файлов
    val fileLinks = parsedHtml.select("div.dlcls > div > a[href]").eachAttr("href").asScala
    //селектор получения ссылки на "следующую" страницу
    val nextPageLinks = parsedHtml.select("div.cls_pagination > div > a[title=\"Next page\"]").eachAttr("href").asScala
    (fileLinks, nextPageLinks)
  }
}

case class ProcessCatalogPageResult(uri: String, fileLinks: Iterable[String], nextPageLinks: Iterable[String])
