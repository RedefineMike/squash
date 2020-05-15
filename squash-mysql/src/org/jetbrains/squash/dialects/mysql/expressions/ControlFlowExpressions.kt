package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.expressions.Expression
import org.jetbrains.squash.expressions.GeneralFunctionExpression

/**
 * If [expression] is not NULL, IFNULL() returns [expression]; otherwise it returns [whenNull].
 * See : [MySQL : IfNull()](https://dev.mysql.com/doc/refman/en/control-flow-functions.html#function_ifnull)
 */
fun <T> ifNull(expression: Expression<T>, whenNull:Expression<T>) = GeneralFunctionExpression<T>("IFNULL", listOf(expression, whenNull))

/**
 * Returns NULL if expr1 = expr2 is true, otherwise returns expr1. This is the same as CASE WHEN expr1 = expr2 THEN NULL ELSE expr1 END.
 * The return value has the same type as the first argument.
 * See : [MySQL : nullIf()](https://dev.mysql.com/doc/refman/en/control-flow-functions.html#function_nullif)
 */
fun <T> Expression<T>.nullIf(expression:Expression<T>) = GeneralFunctionExpression<T>("NULLIF", listOf(this, expression))