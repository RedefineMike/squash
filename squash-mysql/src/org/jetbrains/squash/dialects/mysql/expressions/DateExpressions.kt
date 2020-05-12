package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.dialect.DialectExtension
import org.jetbrains.squash.dialect.SQLDialect
import org.jetbrains.squash.dialect.SQLStatementBuilder
import org.jetbrains.squash.expressions.ColumnFunctionExpression
import org.jetbrains.squash.expressions.Expression
import org.jetbrains.squash.expressions.FunctionExpression
import org.jetbrains.squash.expressions.LiteralExpression
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 *  Extract the date part of a date or datetime expression
 */
fun Expression<*>.date() = ColumnFunctionExpression<LocalDate>("DATE", this)

/**
 * Subtracts a number of days from a date.
 */
fun Expression<*>.dateSub(expression:Expression<Number>, timeUnit:ChronoUnit = ChronoUnit.DAYS) = MysqlDateMathFunction("DATE_SUB", this, MysqlTimeInterval.of(expression, timeUnit))
fun Expression<*>.dateSub(value:Number, timeUnit:ChronoUnit = ChronoUnit.DAYS) = dateSub(LiteralExpression(value), timeUnit)

/**
 *  Add a time value (using Java [ChronoUnit]) to a date
 */
fun Expression<*>.dateAdd(expression:Expression<Number>, timeUnit:ChronoUnit = ChronoUnit.DAYS) = MysqlDateMathFunction("DATE_ADD", this, MysqlTimeInterval.of(expression, timeUnit))
fun Expression<*>.dateAdd(value:Number, timeUnit:ChronoUnit = ChronoUnit.DAYS) = dateAdd(LiteralExpression(value), timeUnit)

/**
 * Return the year part of a date.
 */
fun Expression<*>.year() = ColumnFunctionExpression<Int>("YEAR", this)

/**
 *  Return the month from the date passed.
 */
fun Expression<*>.month() = ColumnFunctionExpression<Int>("MONTH", this)

/**
 * Return the day of the month part of a date.
 */
fun Expression<*>.day() = dayOfMonth()

/**
 * Return the day of the month part of a date.
 */
fun Expression<*>.dayOfMonth() = ColumnFunctionExpression<Int>("DAYOFMONTH", this)

/**
 *  Return the numeric weekday from a date (1 = Sunday, 2 = Monday, …, 7 = Saturday).
 *  This function conforms to ODBC standard.
 */
fun Expression<*>.dayOfWeek() = ColumnFunctionExpression<Int>("DAYOFWEEK", this)

/**
 *  Return the numeric weekday index from a date (0 = Monday, 1 = Tuesday, … 6 = Sunday).
 */
fun Expression<*>.weekDay() = ColumnFunctionExpression<Int>("WEEKDAY", this)

/**
 * Return the numeric day of the year from a date.
 */
fun Expression<*>.dayOfYear() = ColumnFunctionExpression<Int>("DAYOFYEAR", this)

/**
 * Truncate a date to the given date part.
 */
fun Expression<LocalDateTime>.truncateTo(timeUnit:ChronoUnit, offset:Int = 0) = DateTruncateExpression(MysqlTimeInterval.of(this, timeUnit, offset))

class DateTruncateExpression(
	private val interval:MysqlTimeIntervalExpression
) : DialectExtension, Expression<LocalDateTime> {
	
	override fun appendTo(builder: SQLStatementBuilder, dialect: SQLDialect) {
		builder.append("(DATE(")
		dialect.appendExpression(builder, interval.value)
		builder.append(") + ")
		interval.appendTo(builder, dialect)
		builder.append(")")
	}

}

/*
 * Date Math
 */

class MysqlDateMathFunction(val name:String, val expression:Expression<*>, val interval:MysqlTimeIntervalStatic) : FunctionExpression<LocalDateTime>

/*
 * Time Intervals
 */

class MysqlTimeIntervalStatic(
	val value:Expression<Number>,
	val unit:String
) : MysqlTimeInterval {

	override fun appendTo(builder: SQLStatementBuilder, dialect: SQLDialect) { builder.append("INTERVAL $value $unit") }
}

class MysqlTimeIntervalExpression(
	val value:Expression<LocalDateTime>,
	val unit:String,
	val offset:Int = 0
) : MysqlTimeInterval {

	override fun appendTo(builder: SQLStatementBuilder, dialect: SQLDialect) {
		builder.append("INTERVAL EXTRACT($unit FROM ")
		dialect.appendExpression(builder, value)
		builder.append(")${if (offset != 0) "+ $offset" else ""} $unit")
	}
}

interface MysqlTimeInterval : DialectExtension {

	companion object {
		/**
		 * Creates a [MysqlTimeIntervalStatic] value from a Java [ChronoUnit].
		 */
		fun of(value:Expression<Number>, timeUnit:ChronoUnit) = MysqlTimeIntervalStatic(value, chronoUnitToIntervalUnit(timeUnit))

		fun of(value:Expression<LocalDateTime>, timeUnit:ChronoUnit, offset:Int = 0) = MysqlTimeIntervalExpression(value, chronoUnitToIntervalUnit(timeUnit), offset)
		
		private fun chronoUnitToIntervalUnit(timeUnit:ChronoUnit) = when (timeUnit) {
			ChronoUnit.MICROS -> "MICROSECOND"
			ChronoUnit.SECONDS -> "SECOND"
			ChronoUnit.MINUTES -> "MINUTE"
			ChronoUnit.HOURS -> "HOUR"
			ChronoUnit.DAYS -> "DAY"
			ChronoUnit.MONTHS -> "MONTH"
			ChronoUnit.YEARS -> "YEAR"
			ChronoUnit.WEEKS -> "WEEK"
			else -> error("ChronoUnit not supported by MySQL intervals.")
		}
	}
}