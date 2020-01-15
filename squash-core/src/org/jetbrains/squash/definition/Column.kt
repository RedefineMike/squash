package org.jetbrains.squash.definition

import org.jetbrains.squash.query.*

/**
 * Represents a column in a database [Table]
 *
 * Column is also a [NamedExpression] which allows it to be used in expressions.
 *
 * @param V type of the value in this column
 */
interface Column<out V> : NamedExpression<Name, V> {
    /**
     * [CompoundElement] to which this column belongs
     */
    val compound: CompoundElement

    /**
     * Database type of the column
     */
    val type: ColumnType

    /**
     * List of additional properties of the column, like autoincrement, nullable, etc
     */
    val properties: List<ColumnProperty>
}

inline fun <reified T : ColumnProperty> Column<*>.hasProperty(): Boolean {
    return properties.any { it is T }
}

inline fun <reified T : ColumnProperty> Column<*>.propertyOrNull(): T? {
    return properties.filterIsInstance<T>().singleOrNull()
}

open class ColumnDefinition<out V>(final override val compound: TableDefinition, name: Identifier, override val type: ColumnType) : Column<V> {
    override fun toString(): String = "$name: $type"
    override val properties = mutableListOf<ColumnProperty>()
    override val name = QualifiedIdentifier<Name>(compound.compoundName, name)
}

class ReferenceColumn<out V>(compound: TableDefinition, name: Identifier, val reference: Column<V>) : ColumnDefinition<V>(compound, name, ReferenceColumnType(reference.type)) {
    override fun toString(): String = "&$reference"
}


/*
 * Column Conversion Functions
 */

/**
 * Converts this Int ColumnDefinition to a Long ColumnDefinition. This is
 * useful for comparing columns of different numeric types when valid.
 */
fun ColumnDefinition<Int?>.asLong() = ColumnDefinition<Long?>(this.compound, this.name.identifier, this.type)

