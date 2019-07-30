package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.expressions.GeneralFunctionExpression

fun rand(seed:Int? = null) = GeneralFunctionExpression<Double>("RAND", if (seed != null) listOf(seed) else null)
