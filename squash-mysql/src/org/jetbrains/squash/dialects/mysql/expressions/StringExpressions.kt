package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.expressions.Expression
import org.jetbrains.squash.expressions.GeneralFunctionExpression

/**
 * Returns the string that results from concatenating the arguments. May have one or more arguments. If all arguments are nonbinary strings, the result is a nonbinary string. If the arguments include any binary strings, the result is a binary string. A numeric argument is converted to its equivalent nonbinary string form.
 * See : https://dev.mysql.com/doc/refman/5.7/en/string-functions.html#function_concat
 */
fun concat(vararg expressions:Expression<String?>) = GeneralFunctionExpression<String?>("CONCAT", expressions.toList())

/**
 *  For a string argument str, HEX() returns a hexadecimal string representation of str where each byte of each character in str is converted to two hexadecimal digits. (Multibyte characters therefore become more than two digits.) The inverse of this operation is performed by the UNHEX() function.
 *  For a numeric argument N, HEX() returns a hexadecimal string representation of the value of N treated as a longlong (BIGINT) number. This is equivalent to CONV(N,10,16). The inverse of this operation is performed by CONV(HEX(N),16,10).
 *  See : https://dev.mysql.com/doc/refman/5.7/en/string-functions.html#function_hex
 */
fun hex(value:String) = GeneralFunctionExpression<String>("HEX", listOf(value))
/**
 *  For a string argument str, HEX() returns a hexadecimal string representation of str where each byte of each character in str is converted to two hexadecimal digits. (Multibyte characters therefore become more than two digits.) The inverse of this operation is performed by the UNHEX() function.
 *  For a numeric argument N, HEX() returns a hexadecimal string representation of the value of N treated as a longlong (BIGINT) number. This is equivalent to CONV(N,10,16). The inverse of this operation is performed by CONV(HEX(N),16,10).
 *  See : https://dev.mysql.com/doc/refman/5.7/en/string-functions.html#function_hex
 */
fun hex(value:Long) = GeneralFunctionExpression<String>("HEX", listOf(value))

/**
 * For a string argument str, UNHEX(str) interprets each pair of characters in the argument as a hexadecimal number and converts it to the byte represented by the number. The return value is a binary string.
 * See : https://dev.mysql.com/doc/refman/5.7/en/string-functions.html#function_unhex
 */
fun unhex(value:String) = GeneralFunctionExpression<String>("UNHEX", listOf(value))
