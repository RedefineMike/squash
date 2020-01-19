package org.jetbrains.squash.dialect

import org.jetbrains.squash.definition.*
import org.jetbrains.squash.expressions.*
import org.jetbrains.squash.query.*
import org.jetbrains.squash.statements.*

open class BaseSQLDialect(val name: String) : SQLDialect {
    override val definition: DefinitionSQLDialect = BaseDefinitionSQLDialect(this)

    override fun nameSQL(name: Name): String = when (name) {
        is QualifiedIdentifier<*> -> "${nameSQL(name.parent)}.${nameSQL(name.identifier)}"
        is Identifier -> idSQL(name)
        else -> error("Name '$name' is not supported by ${this@BaseSQLDialect}")
    }

    override fun idSQL(name: Name): String {
        val id = name.id
        return if (isSqlIdentifier(id)) id else "\"$id\""
    }

    @Suppress("NOTHING_TO_INLINE")
	private inline fun Char.isIdentifierStart(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this == '_'

    open fun isSqlIdentifier(id: String): Boolean {
        if (id.isEmpty()) return false
        if (id.toUpperCase() in SQL92_2003.keywords) return false
        return id[0].isIdentifierStart() && id.all { it.isIdentifierStart() || it in '0'..'9' }
    }

	/**
	 * Appends the SQL for a single literal value, such as a query parameter or NULL.
	 */
    override fun appendLiteralSQL(builder: SQLStatementBuilder, value: Any?) {
        if (value != null)
            builder.append("?")
        when (value) {
            null -> builder.append("NULL")
            else -> builder.appendArgument(value)
        }
    }

	/**
	 * Appends the SQL for a single "declaration" expression, meaning each element in a select statement (columnName, tablename.*, columnName AS aliasname, ...). 
	 */
    open fun <T> appendDeclarationExpression(builder: SQLStatementBuilder, expression: Expression<T>): Unit = with(builder) {
        when (expression) {
            is AllTableColumnsExpression -> {
                val element = expression.element
                val name = when (element) {
                    is Table -> nameSQL(element.compoundName)
                    is AliasCompoundElement -> nameSQL(element.label)
                    else -> error("Expression '$element' is not supported by ${this@BaseSQLDialect}")
                }
                append(name)
                append(".*")
            }
            is AliasExpression<T> -> {
                appendExpression(this, expression.expression)
                append(" AS ${nameSQL(expression.name)}")
            }
            is AliasColumn<T> -> {
                appendExpression(this, expression.column)
                append(" AS ${nameSQL(expression.label)}")
            }
            else -> appendExpression(this, expression)
        }
    }

	/**
	 * Appends the SQL for any single [Expression]. This method is used generally for all expressions
	 * and often delegates the SQL generation to other methods, such as [appendBinaryExpression] or [appendFunctionExpression].
	 */
    override fun <T> appendExpression(builder: SQLStatementBuilder, expression: Expression<T>): Unit = with(builder) {
        when (expression) {
            is LiteralExpression -> appendLiteralSQL(this, expression.literal)
            is AliasColumn<T> -> {
                append(nameSQL(expression.label))
            }
            is NamedExpression<*, T> -> append(nameSQL(expression.name))
            is InExpression<*> -> {
                appendExpression(this, expression.value)
                append(" IN (")
                expression.values.forEachIndexed { index, value ->
                    if (index > 0)
                        append(", ")
                    appendLiteralSQL(this, value)
                }
                append(")")
            }
            is BinaryExpression<*, *, *> -> {
                appendBinaryExpression(this, expression)
            }
            is NotExpression -> {
                append("NOT ")
                appendExpression(this, expression.operand)
            }
            is SubQueryExpression<*> -> {
                append("(")
                appendSelectSQL(this, expression.query)
                append(")")
            }
            is FunctionExpression -> {
                appendFunctionExpression(this, expression)
            }
			is IsNullExpression -> {
				appendExpression(this, expression.operand)
				if (expression.notNull) {
					append(" IS NOT NULL")
				} else {
					append(" IS NULL")
				}
			}
			is CaseExpression<*> -> {
				appendCaseExpression(this, expression)
			}
            is DialectExtension -> {
                expression.appendTo(this, this@BaseSQLDialect)
            }
            else -> error("Expression '$expression' is not supported by ${this@BaseSQLDialect}")
        }
    }

	/**
	 * Appends the SQL for a single [BinaryExpression] (left, operator, right) element.
	 */
    open fun appendBinaryExpression(builder: SQLStatementBuilder, expression: BinaryExpression<*, *, *>) = with(builder) {
        if (expression.right is LiteralExpression && expression.right.literal == null) {
            when (expression) {
                is EqExpression<*> -> {
                    appendExpression(this, expression.left)
                    append(" IS NULL")
                }
                is NotEqExpression<*> -> {
                    appendExpression(this, expression.left)
                    append(" IS NOT NULL")
                }
                else -> error("NULL can only be used in equality operations, but an expression was ${expression.javaClass.simpleName}")
            }
        } else {
			when (expression) {
				is AndOrExpression -> {
					if (expression.left is AndOrExpression && expression.left.isNestedBinaryExpression) {
						append("( ")
						appendExpression(this, expression.left)
						append(" )")
					} else {
						appendExpression(this, expression.left)
					}
					append(" ")
					appendBinaryOperator(this, expression)
					append(" ")
					if (expression.right is AndOrExpression && expression.right.isNestedBinaryExpression) {
						append("(")
						appendExpression(this, expression.right)
						append(")")
					} else {
						appendExpression(this, expression.right)
					}
				}
				else -> {
					appendExpression(this, expression.left)
					append(" ")
					appendBinaryOperator(this, expression)
					append(" ")
					appendExpression(this, expression.right)
				}
			}
        }
    }

	/**
	 * Appends the appropriate operator (+, - , =, LIKE, IN, ...) for any [BinaryExpression] SQL.
	 */
    open fun appendBinaryOperator(builder: SQLStatementBuilder, expression: BinaryExpression<*, *, *>) = with(builder) {
        append(when (expression) {
            is EqExpression<*> -> "="
            is NotEqExpression<*> -> "<>"
            is LessExpression<*> -> "<"
            is GreaterExpression<*> -> ">"
            is LessEqExpression<*> -> "<="
            is GreaterEqExpression<*> -> ">="
            is AndOrExpression -> if (expression.isOrExpression) "OR" else "AND"
            is PlusExpression -> "+"
            is MinusExpression -> "-"
            is MultiplyExpression -> "*"
            is DivideExpression -> "/"
            is LikeExpression -> "LIKE"
			is InSubQueryExpression -> "IN"
            else -> error("Expression '$expression' is not supported by $this")
        })
    }

	/**
	 * Appends SQL for a CASE statement, as defined by SQL-92 standard.
	 */
	open fun <T> appendCaseExpression(builder: SQLStatementBuilder, expression: CaseExpression<T>) = with(builder) {
		if (expression.target == null) {
			append("CASE")	
		} else {
			append("CASE (")
			appendExpression(this, expression.target)
			append(")")
		}
		
		for (whenThenClause in expression.clauses) {
			append(" WHEN (")
				appendExpression(this, whenThenClause.whenClause)
			append(") THEN ")
				appendExpression(this, whenThenClause.thenClause)
		}
		
		if (expression.finalClause != null) {
			append(" ELSE ")
				appendExpression(this, expression.finalClause!!)
		}
		
		append(" END")
	}

	/**
	 * Appends SQL for any function expression, such as COUNT(column), or any [GeneralFunctionExpression]
	 */
    open fun <T> appendFunctionExpression(builder: SQLStatementBuilder, expression: FunctionExpression<T>) = with(builder) {
        when (expression) {
            is CountExpression -> {
				if (expression.value == null) {
					append("COUNT(*)")
				} else {
					append("COUNT(")
					appendExpression(this, expression.value)
					append(")")
				}
            }
            is CountDistinctExpression -> {
				if (expression.value == null) {
					append("COUNT(DISTINCT *)")
				} else {
					append("COUNT(DISTINCT ")
					appendExpression(this, expression.value)
					append(")")
				}
            }
			is ColumnFunctionExpression -> {
				append("${expression.name}(")
				appendExpression(this, expression.value)
				append(")")
			}
			is GeneralFunctionExpression<*> -> {
				append("${expression.name}(")
				expression.arguments?.forEachIndexed { index, arg ->
					// Add a comma between arguments after the first one
					if (index > 0) {
						append(", ")
					}

					// Handle column expressions vs constant values / primitive arguments
					when (arg) {
						is Expression<*> -> appendExpression(this, arg)
						else -> {
							append("?")
							appendArgument(arg)
						}
					}
				}
				append(")")
			}
            else -> error("Function '$expression' is not supported by ${this@BaseSQLDialect}")
        }
    }

	/**
	 * Appends SQL for the SELECT clause.
	 */
    open fun appendSelectSQL(builder: SQLStatementBuilder, query: Query) {
        builder.append("SELECT ")
        if (query.selection.isEmpty()) {
            builder.append("*")
        } else {
            query.selection.forEachIndexed { index, expression ->
                if (index != 0) builder.append(", ")
                appendDeclarationExpression(builder, expression)
            }
        }
        appendQuerySQL(builder, query)
    }

	/**
	 * Appends SQL for the ORDER BY clause.
	 */
    open fun appendOrderSQL(builder: SQLStatementBuilder, query: Query) {
        if (query.order.isEmpty())
            return

        builder.append(" ORDER BY ")
        query.order.forEachIndexed { index, order ->
            if (index != 0) builder.append(", ")
            appendOrderExpression(builder, order)
        }
    }

	/**
	 * Appends the SQL for all "modifiers", such as LIMIT / OFFSET.
	 */
    open fun appendModifiersSQL(builder: SQLStatementBuilder, query: Query) {
        if (query.modifiers.isEmpty())
            return
        query.modifiers.forEach {
            appendModifierSQL(builder, it)
        }
    }

	/**
	 * Appends SQL for a single modifier statement, such as LIMIT / OFFSET.
	 */
    open fun appendModifierSQL(builder: SQLStatementBuilder, modifier: QueryModifier) {
        when (modifier) {
            is QueryLimit -> {
                builder.append(" LIMIT ?")
                builder.appendArgument(modifier.limit)
                if (modifier.offset != 0L) {
                    builder.append(" OFFSET ?")
                    builder.appendArgument(modifier.offset)
                }
            }
            else -> error("Query modifier $modifier is not supported by ${this@BaseSQLDialect}")
        }
    }

	/**
	 * Appends SQL for the GROUP BY clause.
	 */
    open fun appendGroupingSQL(builder: SQLStatementBuilder, query: Query) {
        if (query.grouping.isEmpty())
            return

        builder.append(" GROUP BY ")
        query.grouping.forEachIndexed { index, order ->
            if (index != 0) builder.append(", ")
            appendExpression(builder, order)
        }
    }

	/**
	 * Appends SQL for the HAVING clause.
	 */
    open fun appendHavingSQL(builder: SQLStatementBuilder, query: Query) {
        if (query.having.isEmpty())
            return

        builder.append(" HAVING ")
        query.having.forEachIndexed { index, order ->
            if (index != 0) builder.append(" AND ")
            appendExpression(builder, order)
        }
    }

	/**
	 * Appends SQL for a single element of the ORDER BY clause.
	 */
    open fun appendOrderExpression(builder: SQLStatementBuilder, order: QueryOrder) {
        appendExpression(builder, order.expression)
        when (order) {
            is QueryOrder.Ascending -> { /* ASC is default */
            }
            is QueryOrder.Descending -> builder.append(" DESC")
        }
        builder.append(" NULLS LAST")
    }

	/**
	 * Appends SQL for the WHERE clause.
	 */
    open fun appendFilterSQL(builder: SQLStatementBuilder, query: Query) {
        if (query.filter.isEmpty())
            return

        builder.append(" WHERE ")
        query.filter.forEachIndexed { index, expression ->
            if (index != 0) builder.append(" AND ")
            appendExpression(builder, expression)
        }
    }

	/**
	 * Appends SQL for FROM and JOIN clauses.
	 */
    open fun appendCompoundSQL(builder: SQLStatementBuilder, query: Query) {
        if (query.compound.isEmpty())
            return

        val tables = query.compound.filterIsInstance<QueryCompound.From>()
        builder.append(" FROM ")
        tables.forEach {
            appendCompoundElementSQL(builder, it.element)
        }

        val innerJoins = query.compound.filter { it !is QueryCompound.From }
        if (innerJoins.any()) {
            innerJoins.forEach { join ->
                val condition = when (join) {
                    is QueryCompound.InnerJoin -> {
                        builder.append(" INNER JOIN ")
                        join.condition
                    }
                    is QueryCompound.LeftOuterJoin -> {
                        builder.append(" LEFT OUTER JOIN ")
                        join.condition
                    }
                    is QueryCompound.RightOuterJoin -> {
                        builder.append(" RIGHT OUTER JOIN ")
                        join.condition
                    }
                    else -> error("${join::class.simpleName} clauses should have been filtered out")
                }
                appendCompoundElementSQL(builder, join.element)
                builder.append(" ON ")
                appendExpression(builder, condition)
            }
        }
		
    }

	/**
	 * Appends SQL for UNION clauses.
	 */
	open fun appendUnionSQL(builder: SQLStatementBuilder, query: Query) {
		if (query.unions.isEmpty())
			return

		for (union in query.unions) {
			builder.append(" UNION ${union.type.name} ")
			appendSelectSQL(builder, union.query)
		}
	}
	
    open fun appendQuerySQL(builder: SQLStatementBuilder, query: Query): Unit = with(builder) {
        appendCompoundSQL(builder, query)
        appendFilterSQL(builder, query)
        appendGroupingSQL(builder, query)
        appendHavingSQL(builder, query)
        appendOrderSQL(builder, query)
        appendModifiersSQL(builder, query)
		appendUnionSQL(builder, query)
    }

    override fun appendCompoundElementSQL(builder: SQLStatementBuilder, element: CompoundElement): Unit = with(builder) {
        when (element) {
            is Table -> builder.append(nameSQL(element.compoundName))
            is AliasCompoundElement -> {
                appendCompoundElementSQL(this, element.element)
                builder.append(" AS " + nameSQL(element.label))
            }
            is QueryStatement -> {
                append("(")
                appendSelectSQL(this, element)
                append(")")
            }
            else -> error("Compound '$element' is not supported by ${this@BaseSQLDialect}")
        }
    }

    override fun <T> statementSQL(statement: Statement<T>): SQLStatement = when (statement) {
        is QueryStatement -> queryStatementSQL(statement)
        is InsertValuesStatement<*, *> -> insertValuesStatementSQL(statement)
        is InsertQueryStatement<*> -> insertQueryStatementSQL(statement)
        is UpdateQueryStatement<*> -> updateQueryStatementSQL(statement)
        is DeleteQueryStatement<*> -> deleteQueryStatementSQL(statement)
        else -> error("Statement '$statement' is not supported by ${this@BaseSQLDialect}")
    }

    open fun queryStatementSQL(query: QueryStatement): SQLStatement {
        return SQLStatementBuilder().apply { appendSelectSQL(this, query) }.build()
    }

    open fun updateQueryStatementSQL(statement: UpdateQueryStatement<*>): SQLStatement = SQLStatementBuilder().apply {
        append("UPDATE ")
        append(nameSQL(statement.table.compoundName))
        append(" SET ")
        val values = statement.values.toList() // fix order
        values.forEachIndexed { index, value ->
            if (index > 0)
                append(", ")
            append(idSQL(value.first.name))
            append(" = ")
            appendExpression(this, value.second)
        }
        append(" ")
        appendQuerySQL(this, statement)
    }.build()

    open fun deleteQueryStatementSQL(statement: DeleteQueryStatement<*>): SQLStatement = SQLStatementBuilder().apply {
        append("DELETE FROM ")
        append(nameSQL(statement.table.compoundName))
        append(" ")
        appendQuerySQL(this, statement)
    }.build()

    open fun insertQueryStatementSQL(statement: InsertQueryStatement<*>): SQLStatement = SQLStatementBuilder().apply {
        append("INSERT INTO ")
        append(nameSQL(statement.table.compoundName))
        append(" ")
        appendSelectSQL(this, statement)
    }.build()

    open fun insertValuesStatementSQL(statement: InsertValuesStatement<*, *>): SQLStatement = SQLStatementBuilder().apply {
        append("INSERT INTO ")
        append(nameSQL(statement.table.compoundName))
        append(" (")
        val values = statement.values.entries.toList() // fix order
        values.forEachIndexed { index, value ->
            if (index > 0)
                append(", ")
            append(idSQL(value.key.name))
        }
        append(") VALUES (")
        values.forEachIndexed { index, value ->
            if (index > 0)
                append(", ")
            appendLiteralSQL(this, value.value)
        }
        append(")")
    }.build()

    override fun toString(): String = "SQLDialect '$name'"
}
