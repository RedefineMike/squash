package org.jetbrains.squash.dialects.mysql.tests

import org.jetbrains.squash.dialects.mysql.month
import org.jetbrains.squash.expressions.literal
import org.jetbrains.squash.query.select
import org.jetbrains.squash.results.get
import org.jetbrains.squash.tests.*
import org.junit.Test
import java.time.LocalDate
import kotlin.test.assertEquals

class MySqlQueryTests : QueryTests(), DatabaseTests by MySqlDatabaseTests() {
    override fun nullsLast(sql: String): String {
        return "ISNULL(${sql.removeSuffix(" DESC")}), $sql"
    }

	@Test
	fun mysqlDatePartFunctions() {
		withTables {
			
			val query = select {
				literal(LocalDate.of(2001, 12, 1)).month()
			}
			
			connection.dialect.statementSQL(query).assertSQL {
				"SELECT MONTH(?)"
			}

			val month = query.execute().single().get<Int>(0)
			assertEquals(12, month)
		}
	}
}