package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.dialect.DialectExtension
import org.jetbrains.squash.dialect.SQLDialect
import org.jetbrains.squash.dialect.SQLStatementBuilder
import org.jetbrains.squash.expressions.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
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
 * Convert a date to the given timezone. Corresponds to the MySQL convert_tz(date, from_tz, to_tz) function.
 * Differs from MySQL in that the from timezone is defaulted to UTC.
 * See : https://dev.mysql.com/doc/refman/en/date-and-time-functions.html#function_convert-tz
 */
fun Expression<LocalDateTime>.convertTimeZone(toTimezone:ZoneId, fromTimezone:ZoneId = ZoneId.of("UTC")) = GeneralFunctionExpression<LocalDateTime>("convert_tz", listOf(this, literal(fromTimezone.id), literal(toTimezone.id)))

/**
 * Truncate a date to the given date part. Currently, only supports time units as large as [ChronoUnit.DAYS].
 */
fun Expression<LocalDateTime>.truncateTo(timeUnit:ChronoUnit, toTimezone:ZoneId? = null, fromTimezone:ZoneId = ZoneId.of("UTC"), offset:Int = 0) = DateTruncateExpression(this, timeUnit, toTimezone, fromTimezone, offset)

class DateTruncateExpression(
	expression:Expression<LocalDateTime>,
	val timeUnit:ChronoUnit,
	val toTimezone:ZoneId? = null,
	val fromTimezone:ZoneId = ZoneId.of("UTC"),
	val offset:Int = 0
) : DialectExtension, Expression<LocalDateTime> {
	
	val expression = toTimezone?.let { expression.convertTimeZone(toTimezone, fromTimezone) } ?: expression

	override fun appendTo(builder: SQLStatementBuilder, dialect: SQLDialect) {
		if (timeUnit.duration < ChronoUnit.DAYS.duration) {
			builder.append("(DATE(")
			dialect.appendExpression(builder, expression)
			builder.append(')')
			when (timeUnit) {
				ChronoUnit.HOURS -> {
					builder.append(" + ")
					MysqlTimeInterval.of(this.expression, timeUnit, offset).appendTo(builder, dialect)
				}
				ChronoUnit.MINUTES -> {
					builder.append(" + ")
					MysqlTimeInterval.of(this.expression, ChronoUnit.HOURS).appendTo(builder, dialect)
					builder.append(" + ")
					MysqlTimeInterval.of(this.expression, timeUnit, offset).appendTo(builder, dialect)
				}
				ChronoUnit.SECONDS -> {
					builder.append(" + ")
					MysqlTimeInterval.of(this.expression, ChronoUnit.HOURS).appendTo(builder, dialect)
					builder.append(" + ")
					MysqlTimeInterval.of(this.expression, ChronoUnit.MINUTES).appendTo(builder, dialect)
					builder.append(" + ")
					MysqlTimeInterval.of(this.expression, timeUnit, offset).appendTo(builder, dialect)
				}
				else -> error("Squash SQL Error : $timeUnit is not supported by Expression<LocalDateTime>.truncateTo()")
			}
			builder.append(")")
		} else {
			dialect.appendExpression(builder, when (timeUnit) {
				ChronoUnit.DAYS -> {
					expression.date()
				}
				ChronoUnit.MONTHS -> {
					expression.date().dateSub(expression.dayOfMonth().minus(1))
				}
				ChronoUnit.WEEKS -> {
					expression.date().dateSub(expression.dayOfWeek().minus(1))
				}
				ChronoUnit.YEARS -> {
					expression.date().dateSub(expression.dayOfYear().minus(1))
				}
				else -> error("Squash SQL Error : $timeUnit is not supported by Expression<LocalDateTime>.truncateTo()")
			})
		}
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