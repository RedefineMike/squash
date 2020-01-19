package org.jetbrains.squash.query

class QueryUnion(val type:UnionType = UnionType.ALL, val query:Query)

enum class UnionType {
	DISTINCT, ALL
}