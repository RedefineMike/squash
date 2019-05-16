package org.jetbrains.squash.dialects.mysql

import org.jetbrains.squash.expressions.Expression
import org.jetbrains.squash.expressions.FunctionExpression
import org.jetbrains.squash.expressions.GeneralFunctionExpression
import java.time.LocalDate
import java.time.LocalDateTime

fun Expression<*>.date() = GeneralFunctionExpression<LocalDate>("DATE", this)
fun Expression<*>.dateSub(interval:TimeInterval.Value) = MysqlDateMathFunction("DATE_SUB", this, interval)
fun Expression<*>.dateSub(days:Long) = MysqlDateMathFunction("DATE_SUB", this, TimeInterval.day(days))
fun Expression<*>.dateAdd(interval:TimeInterval.Value) = MysqlDateMathFunction("DATE_ADD", this, interval)
fun Expression<*>.dateAdd(days:Long) = MysqlDateMathFunction("DATE_ADD", this, TimeInterval.day(days))

fun Expression<*>.month() = GeneralFunctionExpression<Int>("MONTH", this)
fun Expression<*>.year() = GeneralFunctionExpression<Int>("YEAR", this)
fun Expression<*>.weekDay() = GeneralFunctionExpression<Int>("WEEKDAY", this)

/*
 * Date Math
 */

class MysqlDateMathFunction(val name:String, val expression:Expression<*>, val interval:TimeInterval.Value) : FunctionExpression<LocalDateTime>

class TimeInterval {
	companion object {
		fun day(days:Long) = Value(days, "DAY")
	}
	
	class Value(
		val value:Any,
		val unit:String
	)
}