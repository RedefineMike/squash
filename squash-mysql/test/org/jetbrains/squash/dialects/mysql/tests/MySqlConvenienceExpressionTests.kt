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
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals

class MySqlConvenienceExpressionTests : DatabaseTests by MySqlDatabaseTests() {

	@Test
	fun testDateTruncation() = withAllColumnTypes {
		val query = AllColumnTypes.select(
			AllColumnTypes.datetime.truncateTo(ChronoUnit.HOURS).alias("originalDate")
		)

		connection.dialect.statementSQL(query).assertSQL {
			"SELECT (DATE(AllColumnTypes.datetime) + INTERVAL EXTRACT(HOUR FROM AllColumnTypes.datetime) HOUR) AS originalDate FROM AllColumnTypes"
		}

		val row = query.execute().single()
		assertEquals("1976-11-24T08:00", row.get<LocalDateTime>("originalDate").toString())
	}

}