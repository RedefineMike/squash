package org.jetbrains.squash.dialects.mysql.tests

import org.jetbrains.squash.dialects.mysql.expressions.truncateTo
import org.jetbrains.squash.expressions.alias
import org.jetbrains.squash.query.select
import org.jetbrains.squash.results.get
import org.jetbrains.squash.tests.DatabaseTests
import org.jetbrains.squash.tests.data.AllColumnTypes
import org.jetbrains.squash.tests.data.withAllColumnTypes
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class MySqlConvenienceExpressionTests : DatabaseTests by MySqlDatabaseTests() {

	@Test
	fun testDateTruncation() = withAllColumnTypes {
		val query = AllColumnTypes.select(
			AllColumnTypes.datetime.alias("originalDate"),
			AllColumnTypes.datetime.truncateTo(ChronoUnit.MINUTES).alias("minutes"),
			AllColumnTypes.datetime.truncateTo(ChronoUnit.HOURS).alias("hours"),
			AllColumnTypes.datetime.truncateTo(ChronoUnit.DAYS).alias("days"),
			AllColumnTypes.datetime.truncateTo(ChronoUnit.WEEKS).alias("weeks"),
			AllColumnTypes.datetime.truncateTo(ChronoUnit.MONTHS).alias("months"),
			AllColumnTypes.datetime.truncateTo(ChronoUnit.YEARS).alias("years")
		)

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT AllColumnTypes.datetime AS originalDate, (DATE(AllColumnTypes.datetime) + INTERVAL EXTRACT(HOUR FROM AllColumnTypes.datetime) HOUR + INTERVAL EXTRACT(MINUTE FROM AllColumnTypes.datetime) MINUTE) AS minutes, (DATE(AllColumnTypes.datetime) + INTERVAL EXTRACT(HOUR FROM AllColumnTypes.datetime) HOUR) AS hours, DATE(AllColumnTypes.datetime) AS days, DATE_SUB(DATE(AllColumnTypes.datetime), INTERVAL DAYOFWEEK(AllColumnTypes.datetime) - ? DAY) AS weeks, DATE_SUB(DATE(AllColumnTypes.datetime), INTERVAL DAYOFMONTH(AllColumnTypes.datetime) - ? DAY) AS months, DATE_SUB(DATE(AllColumnTypes.datetime), INTERVAL DAYOFYEAR(AllColumnTypes.datetime) - ? DAY) AS years FROM AllColumnTypes"
		}

		val result = query.execute().single()
		assertEquals(LocalDateTime.of(1976, 11, 24, 8, 22, 0), result["originalDate"], "PST Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 24, 8, 22, 0), result["minutes"], "Minute Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 24, 8, 0, 0), result["hours"], "Hour Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 24, 0, 0, 0), result["days"], "Day Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 21, 0, 0, 0), result["weeks"], "Week Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 1, 0, 0, 0), result["months"], "Month Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 1, 1, 0, 0, 0), result["years"], "Year Truncated Date Does Not Match")
	}

	@Test
	fun testDateTruncationWithTimezone() = withAllColumnTypes {
		val timezone = ZoneId.of("US/Samoa", ZoneId.SHORT_IDS) // UTC-11
		
		val query = AllColumnTypes.select(
				AllColumnTypes.datetime.alias("originalDate"),
				AllColumnTypes.datetime.truncateTo(ChronoUnit.HOURS, timezone).alias("hours"),
				AllColumnTypes.datetime.truncateTo(ChronoUnit.DAYS, timezone).alias("days"),
				AllColumnTypes.datetime.truncateTo(ChronoUnit.WEEKS, timezone).alias("weeks"),
				AllColumnTypes.datetime.truncateTo(ChronoUnit.MONTHS, timezone).alias("months"),
				AllColumnTypes.datetime.truncateTo(ChronoUnit.YEARS, timezone).alias("years")
		)

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT AllColumnTypes.datetime AS originalDate, (DATE(convert_tz(AllColumnTypes.datetime, ?, ?)) + INTERVAL EXTRACT(HOUR FROM convert_tz(AllColumnTypes.datetime, ?, ?)) HOUR) AS hours, DATE(convert_tz(AllColumnTypes.datetime, ?, ?)) AS days, DATE_SUB(DATE(convert_tz(AllColumnTypes.datetime, ?, ?)), INTERVAL DAYOFWEEK(convert_tz(AllColumnTypes.datetime, ?, ?)) - ? DAY) AS weeks, DATE_SUB(DATE(convert_tz(AllColumnTypes.datetime, ?, ?)), INTERVAL DAYOFMONTH(convert_tz(AllColumnTypes.datetime, ?, ?)) - ? DAY) AS months, DATE_SUB(DATE(convert_tz(AllColumnTypes.datetime, ?, ?)), INTERVAL DAYOFYEAR(convert_tz(AllColumnTypes.datetime, ?, ?)) - ? DAY) AS years FROM AllColumnTypes".also { println(it) }
		}

		val result = query.execute().single()
		assertEquals(LocalDateTime.of(1976, 11, 24, 8, 22, 0), result["originalDate"], "PST Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 23, 21, 0, 0), result["hours"], "Hour Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 23, 0, 0, 0), result["days"], "Day Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 21, 0, 0, 0), result["weeks"], "Week Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 11, 1, 0, 0, 0), result["months"], "Month Truncated Date Does Not Match")
		assertEquals(LocalDateTime.of(1976, 1, 1, 0, 0, 0), result["years"], "Year Truncated Date Does Not Match")
	}

}