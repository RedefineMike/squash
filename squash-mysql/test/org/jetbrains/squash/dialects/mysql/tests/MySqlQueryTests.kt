package org.jetbrains.squash.dialects.mysql.tests

import org.jetbrains.squash.dialects.mysql.expressions.*
import org.jetbrains.squash.expressions.alias
import org.jetbrains.squash.expressions.eq
import org.jetbrains.squash.expressions.literal
import org.jetbrains.squash.query.*
import org.jetbrains.squash.results.get
import org.jetbrains.squash.tests.DatabaseTests
import org.jetbrains.squash.tests.QueryTests
import org.jetbrains.squash.tests.data.*
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class MySqlQueryTests : QueryTests(), DatabaseTests by MySqlDatabaseTests() {
    override fun nullsLast(sql: String): String {
        return "ISNULL(${sql.removeSuffix(" DESC")}), $sql"
    }

	@Test
	fun mysqlDatePartFunctions() = withAllColumnTypes {
		val query = AllColumnTypes.select(
			AllColumnTypes.datetime.year().alias("yearPart"),
			AllColumnTypes.datetime.month().alias("monthPart"),
			AllColumnTypes.datetime.day().alias("dayPart"),
			AllColumnTypes.datetime.dayOfWeek().alias("dayOfWeekPart"),
			AllColumnTypes.datetime.weekDay().alias("weekDayPart"),
			AllColumnTypes.datetime.dayOfYear().alias("dayOfYearPart"),
			AllColumnTypes.datetime.date().alias("dateOnly")
		)
		
		connection.dialect.statementSQL(query).assertSQL {
			"SELECT YEAR(AllColumnTypes.datetime) AS yearPart, MONTH(AllColumnTypes.datetime) AS monthPart, DAYOFMONTH(AllColumnTypes.datetime) AS dayPart, DAYOFWEEK(AllColumnTypes.datetime) AS dayOfWeekPart, WEEKDAY(AllColumnTypes.datetime) AS weekDayPart, DAYOFYEAR(AllColumnTypes.datetime) AS dayOfYearPart, DATE(AllColumnTypes.datetime) AS dateOnly FROM AllColumnTypes"
		}
		
		val result = query.execute().single()
		assertEquals(1976, result["yearPart"], "YEAR() result is incorrect")
		assertEquals(11, result["monthPart"], "MONTH() result is incorrect")
		assertEquals(24, result["dayPart"], "DAYOFMONTH() result is incorrect")
		assertEquals(4, result["dayOfWeekPart"], "DAYOFWEEK() result is incorrect")
		assertEquals(2, result["weekDayPart"], "WEEKDAY() result is incorrect")
		assertEquals(329, result["dayOfYearPart"], "DAYOFYEAR() result is incorrect")
	}

	@Test
	fun mysqlDateMathFunctions() = withAllColumnTypes {
		val query = AllColumnTypes.select(
			AllColumnTypes.datetime.alias("originalDate"),
			
			AllColumnTypes.date.dateSub(1).alias("minusDays"),
			AllColumnTypes.datetime.dateSub(3, ChronoUnit.HOURS).alias("minusHours"),
			
			AllColumnTypes.date.dateAdd(1).alias("plusDays"),
			AllColumnTypes.datetime.dateAdd(3, ChronoUnit.HOURS).alias("plusHours")
		)

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT AllColumnTypes.datetime AS originalDate, DATE_SUB(AllColumnTypes.`date`, INTERVAL ? DAY) AS minusDays, DATE_SUB(AllColumnTypes.datetime, INTERVAL ? HOUR) AS minusHours, DATE_ADD(AllColumnTypes.`date`, INTERVAL ? DAY) AS plusDays, DATE_ADD(AllColumnTypes.datetime, INTERVAL ? HOUR) AS plusHours FROM AllColumnTypes"
		}
		
		val result = query.execute().single()

		assertEquals(LocalDate.of(1976, 11, 23), result["minusDays"], "dateSub(days) result is incorrect")
		assertEquals(LocalDateTime.of(1976, 11, 24, 5, 22), result["minusHours"], "dateSub(3, hours) result is incorrect")
		assertEquals(LocalDate.of(1976, 11, 25), result["plusDays"], "dateAdd(days) result is incorrect")
		assertEquals(LocalDateTime.of(1976, 11, 24, 11, 22), result["plusHours"], "dateAdd(3, hours) result is incorrect")
	}

	@Test
	fun mysqlTimeZoneFunctions() = withAllColumnTypes {
		val query = AllColumnTypes.select(
			AllColumnTypes.datetime.convertTimeZone(ZoneId.of("PST", ZoneId.SHORT_IDS)).alias("pstVersion"),
			AllColumnTypes.datetime.convertTimeZone(ZoneId.of("America/New_York")).alias("estVersion"),
			AllColumnTypes.datetime.alias("original")
		)

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT convert_tz(AllColumnTypes.datetime, ?, ?) AS pstVersion, convert_tz(AllColumnTypes.datetime, ?, ?) AS estVersion, AllColumnTypes.datetime AS original FROM AllColumnTypes"
		}
		
		val result = query.execute().single()
		
		assertEquals(LocalDateTime.of(1976, 11, 24, 8, 22, 0), result["original"], "Original Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 24, 3, 22, 0), result["estVersion"], "EST Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 24, 0, 22, 0), result["pstVersion"], "PST Date Does Not Match")
	}

	@Test
	@Suppress("UNUSED_VARIABLE")
	fun mysqlRandFunction() = withTransaction { 
		val query = select { 
			rand(5).alias("randomNumber")
		}

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT RAND(?) AS randomNumber"
		}

		val result = query.execute().single().get<Double>("randomNumber")
	}
	
	@Test
	@Suppress("UNUSED_VARIABLE")
	fun mysqlRandOrderBy() = withCities {
		val query = Cities
				.select(Cities.id, Cities.name)
				.orderBy { rand() }
				.limit(1)

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT Cities.id, Cities.name FROM Cities ORDER BY ISNULL(RAND()), RAND() LIMIT ?"
		}

		val result = query.execute().single().get<String>("name")
	}
	
	@Test
	fun mysqlGroupConcat() = withCities {

		val munichId = Cities.select(Cities.id).where {
			Cities.name eq "Munich"
		}.execute().single().get(Cities.id)
		
		val query = Cities
				.select(
					Cities.name,
					groupConcat(
						Citizens.name,
						separator = ";",
						orderBy = listOf(QueryOrder.Ascending(Citizens.name))
					).alias("peopleNames")
				).innerJoin(Citizens) {
					Citizens.cityId eq Cities.id
				}.where { 
					Cities.id eq munichId
				}

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT Cities.name, GROUP_CONCAT(Citizens.name ORDER BY Citizens.name SEPARATOR ?) AS peopleNames FROM Cities INNER JOIN Citizens ON Citizens.city_id = Cities.id WHERE Cities.id = ?"
		}

		val result = query.execute().single().get<String>("peopleNames")
		assertEquals("Eugene;Sergey", result, "Group Concat column `peopleNames` are not as expected.")
	}
	
	/*
	 * String Functions
	 */

	@Test
	fun concatFunction() = createTransaction().use { transaction ->
		val connection = transaction.connection
		
		val testQuery = select(
			concat(literal("Hello"), literal(" "), literal("World!")).alias("testResult")
		)

		connection.dialect.statementSQL(testQuery).assertSQL {
			"SELECT CONCAT(?, ?, ?) AS testResult"
		}

		val testResult = testQuery.executeOn(transaction).single().get<String>("testResult")
		assertEquals("Hello World!", testResult)
	}

	@Test
	fun formatFunction() = createTransaction().use { transaction ->
		val connection = transaction.connection

		val testQuery = select(
				format(literal(BigDecimal.valueOf(1234.56789))).alias("testResult")
		)

		connection.dialect.statementSQL(testQuery).assertSQL {
			"SELECT FORMAT(?, ?) AS testResult"
		}

		val testResult = testQuery.executeOn(transaction).single().get<String>("testResult")
		assertEquals("1,234.57", testResult)
	}
	
	@Test
	fun mysqlHexFunctions() = createTransaction().use { transaction ->
		val connection = transaction.connection
		val testValue = "TeStVaLuE"
		
		val queryHex = select(hex(testValue).alias("hexValue"))
		connection.dialect.statementSQL(queryHex).assertSQL {
			"SELECT HEX(?) AS hexValue"
		}

		val hexValue = queryHex.executeOn(transaction).single().get<String>("hexValue")
		assertEquals("5465537456614C7545", hexValue)

		val queryUnhex = select(unhex(hexValue).alias("stringValue"))
		connection.dialect.statementSQL(queryUnhex).assertSQL {
			"SELECT UNHEX(?) AS stringValue"
		}

		val stringValue = queryUnhex.executeOn(transaction).single().get<String>("stringValue")
		assertEquals(testValue, stringValue)
	}
	
	/*
	 * Control Flow Functions
	 */
	@Test
	fun mysqlIfNullFunction() = this.createTransaction().use { transaction ->
		val connection = transaction.connection

		val nonNullValue = "NO NULLS HERE"
		val nullValue:String? = null
		
		val queryNullLast = select(ifNull(literal(nonNullValue), literal(nullValue)).alias("testValue"))
		connection.dialect.statementSQL(queryNullLast).assertSQL {
			"SELECT IFNULL(?, NULL) AS testValue"
		}

		val nullLast = queryNullLast.executeOn(transaction).single().get<String>("testValue")
		assertEquals(nonNullValue, nullLast)

		val queryNullFirst = select(ifNull(literal(nullValue), literal(nonNullValue)).alias("testValue"))
		connection.dialect.statementSQL(queryNullFirst).assertSQL {
			"SELECT IFNULL(NULL, ?) AS testValue"
		}

		val nullFirst = queryNullFirst.executeOn(transaction).single().get<String>("testValue")
		assertEquals(nonNullValue, nullFirst)
	}
	
}