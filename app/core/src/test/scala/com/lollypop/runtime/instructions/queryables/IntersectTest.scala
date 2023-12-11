package com.lollypop.runtime.instructions.queryables

import com.lollypop.language._
import com.lollypop.language.implicits._
import com.lollypop.runtime.implicits.risky._
import com.lollypop.runtime.instructions.VerificationTools
import com.lollypop.runtime.{DatabaseObjectRef, LollypopCompiler, LollypopVM, Scope}
import org.scalatest.funspec.AnyFunSpec
import org.slf4j.LoggerFactory

class IntersectTest extends AnyFunSpec with VerificationTools {
  private val logger = LoggerFactory.getLogger(getClass)
  implicit val compiler: LollypopCompiler = LollypopCompiler()

  describe(classOf[Intersect].getSimpleName) {

    it("should support select .. intersect") {
      val results = compiler.compile(
        s"""|select Symbol, Name, Sector, Industry, SummaryQuote
            |from Customers
            |where Industry == 'Oil/Gas Transmission'
            |   intersect
            |select Symbol, Name, Sector, Industry, SummaryQuote
            |from Customers
            |where Industry == 'Computer Manufacturing'
            |""".stripMargin)
      assert(results == Intersect(
        a = Select(
          fields = List("Symbol".f, "Name".f, "Sector".f, "Industry".f, "SummaryQuote".f),
          from = DatabaseObjectRef("Customers"),
          where = "Industry".f === "Oil/Gas Transmission"),
        b = Select(
          fields = List("Symbol".f, "Name".f, "Sector".f, "Industry".f, "SummaryQuote".f),
          from = DatabaseObjectRef("Customers"),
          where = "Industry".f === "Computer Manufacturing")
      ))
    }

    it("should decompile select .. intersect") {
      verify(
        s"""|select Symbol, Name, Sector, Industry, SummaryQuote
            |from Customers
            |where Industry == 'Oil/Gas Transmission'
            |   intersect
            |select Symbol, Name, Sector, Industry, SummaryQuote
            |from Customers
            |where Industry == 'Computer Manufacturing'
            |""".stripMargin)
    }

    it("should perform an intersect between sources") {
      val (_, _, device) = LollypopVM.searchSQL(Scope(), sql =
        s"""|namespace 'samples.stocks'
            |drop if exists intersect_stocks
            |create table intersect_stocks (
            |   symbol: String(8),
            |   exchange: String(8),
            |   lastSale: Double,
            |   transactions Table (
            |      price Double,
            |      transactionTime DateTime
            |   )[2]
            |)
            |insert into intersect_stocks (symbol, exchange, lastSale, transactions)
            |values ('AAPL', 'NASDAQ', 156.39, '{"price":156.39, "transactionTime":"2021-08-06T15:23:11.000Z"}'),
            |       ('AMD', 'NASDAQ', 56.87, '{"price":56.87, "transactionTime":"2021-08-06T15:23:11.000Z"}'),
            |       ('INTC','NYSE', 89.44, '{"price":89.44, "transactionTime":"2021-08-06T15:23:11.000Z"}'),
            |       ('GMTQ', 'OTCBB', 0.1111, '{"price":0.1111, "transactionTime":"2021-08-06T15:23:11.000Z"}'),
            |       ('AMZN', 'NASDAQ', 988.12, '{"price":988.12, "transactionTime":"2021-08-06T15:23:11.000Z"}'),
            |       ('SHMN', 'OTCBB', 0.0011, '[{"price":0.0010, "transactionTime":"2021-08-06T15:23:11.000Z"},
            |                                   {"price":0.0011, "transactionTime":"2021-08-06T15:23:12.000Z"}]')
            |select symbol, exchange from intersect_stocks
            |  intersect
            |select symbol, exchange from intersect_stocks
            |where symbol is 'INTC' or exchange is 'NASDAQ'
            |""".stripMargin)
      device.tabulate() foreach logger.info
      assert(device.toMapGraph == List(
        Map("symbol" -> "AAPL", "exchange" -> "NASDAQ"),
        Map("symbol" -> "AMD", "exchange" -> "NASDAQ"),
        Map("symbol" -> "INTC", "exchange" -> "NYSE"),
        Map("symbol" -> "AMZN", "exchange" -> "NASDAQ")
      ))
    }

  }

}
