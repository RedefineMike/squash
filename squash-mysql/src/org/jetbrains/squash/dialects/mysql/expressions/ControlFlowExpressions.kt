package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.expressions.Expression
import org.jetbrains.squash.expressions.GeneralFunctionExpression

/**
 * If [expression] is not NULL, IFNULL() returns [expression]; otherwise it returns [whenNull].
 * See : [MySQL : IfNull()](https://dev.mysql.com/doc/refman/5.7/en/control-flow-functions.html#function_ifnull)
 */
fun <T> ifNull(expression: Expression<T>, whenNull:Expression<T>) = GeneralFunctionExpression<T>("IFNULL", listOf(expression, whenNull))
//fun <T> Expression<T>.ifNull(whenNull:Expression<T>) = GeneralFunctionExpression<T>("IFNULL", listOf(expression, whenNull))