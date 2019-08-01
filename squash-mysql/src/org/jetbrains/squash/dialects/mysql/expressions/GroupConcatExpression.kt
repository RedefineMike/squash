package org.jetbrains.squash.dialects.mysql.expressions

import org.jetbrains.squash.dialect.DialectExtension
import org.jetbrains.squash.dialect.SQLDialect
import org.jetbrains.squash.dialect.SQLStatementBuilder
import org.jetbrains.squash.expressions.Expression
import org.jetbrains.squash.query.QueryOrder

/**
 * Implements the MySQL `GROUP_CONCAT` hybrid function / expression.
 * See : https://dev.mysql.com/doc/refman/8.0/en/group-by-functions.html#function_group-concat
 * @param columns The columns to group / concatenate.
 * @param isDistinct Whether only distinct values should be included.
 * @param separator The list separator for the resulting string.
 * @param orderBy A list of [QueryOrder] expressions for ordering concatenated results. 
 */
fun groupConcat(
	vararg columns:Expression<*>,
	isDistinct:Boolean = false,
	separator:String = ",",
	orderBy:List<QueryOrder>? = null
) = GroupConcatExpression(
	columns,
	isDistinct,
	separator,
	orderBy
)

/**
 * Implements the MySQL `GROUP_CONCAT` hybrid function / expression.
 * See : https://dev.mysql.com/doc/refman/8.0/en/group-by-functions.html#function_group-concat
 */
class GroupConcatExpression(
	private val columns: Array<out Expression<*>>,
	private val isDistinct:Boolean,
	private val separator:String,
	private val orderBy:List<QueryOrder>? = null
) : Expression<String>, DialectExtension {

	override fun appendTo(builder:SQLStatementBuilder, dialect: SQLDialect) {
		builder.apply { 
			append("GROUP_CONCAT(")
			if (isDistinct) {
				append("DISTINCT ")
			}
			columns.forEach { 
				dialect.appendExpression(builder, it)
			}
			if (! orderBy.isNullOrEmpty()) {
				append(" ORDER BY ")
				orderBy.forEach {
					dialect.appendExpression(builder, it.expression)
					if (it is QueryOrder.Descending) {
						append(" DESC")
					}
				}
			}
			if (separator != ",") {
				append(" SEPARATOR ?")
				appendArgument(separator)
			}
			append(")")
		}
	}
}