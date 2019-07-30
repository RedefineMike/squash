package org.jetbrains.squash.expressions

import java.math.BigDecimal

interface FunctionExpression<out R> : Expression<R>

/**
 * Represents any function with a name, single column argument, and return value.
 */
class ColumnFunctionExpression<T>(
		val name:String,
		val value:Expression<*>
) : FunctionExpression<T>

class CountExpression(val value: Expression<*>? = null) : FunctionExpression<Long>
class CountDistinctExpression(val value:Expression<*>? = null) : FunctionExpression<Long>

fun Expression<*>.count() = CountExpression(this)
fun Expression<*>.countDistinct() = CountDistinctExpression(this)
fun <T> Expression<T>.min() = ColumnFunctionExpression<T>("MIN",this)
fun <T> Expression<T>.max() = ColumnFunctionExpression<T>("MAX", this)
fun <T> Expression<T>.sum() = ColumnFunctionExpression<T>("SUM",this)
fun Expression<*>.average() = ColumnFunctionExpression<BigDecimal>("AVG",this)
