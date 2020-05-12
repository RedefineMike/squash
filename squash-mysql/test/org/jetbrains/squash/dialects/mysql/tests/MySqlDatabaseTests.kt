package org.jetbrains.squash.dialects.mysql.tests

import com.wix.mysql.EmbeddedMysql
import com.wix.mysql.EmbeddedMysql.anEmbeddedMysql
import com.wix.mysql.SqlScriptSource
import com.wix.mysql.config.MysqldConfig
import com.wix.mysql.config.MysqldConfig.aMysqldConfig
import com.wix.mysql.distribution.Version
import org.jetbrains.squash.connection.DatabaseConnection
import org.jetbrains.squash.definition.ColumnType
import org.jetbrains.squash.definition.IntColumnType
import org.jetbrains.squash.definition.LongColumnType
import org.jetbrains.squash.dialects.mysql.MySqlConnection
import org.jetbrains.squash.tests.DatabaseTests
import java.io.File
import java.util.*
import java.util.zip.GZIPInputStream
import kotlin.test.fail

//val mariadb = DB.newEmbeddedDB(3306).also { it.start() }
val config: MysqldConfig = aMysqldConfig(Version.v5_7_latest)
        .withPort(3306)
        .withUser("user", "")
        .withTimeZone(TimeZone.getDefault())
        .build()

val mysql: EmbeddedMysql = anEmbeddedMysql(config)
        .addSchema("test")
        .start().apply {
			executeScripts("mysql", SqlScriptSource { GZIPInputStream(File("./resources/timezone_posix.sql.gz").inputStream()).bufferedReader(Charsets.US_ASCII).use { reader -> reader.readText() } })
		}

class MySqlDatabaseTests : DatabaseTests {
    override val quote = "`"
    override val indexIfNotExists: String = ""
    override val blobType = "BLOB"
    override fun getIdColumnType(columnType: ColumnType): String = when (columnType) {
        is IntColumnType -> "INT NOT NULL AUTO_INCREMENT"
        is LongColumnType -> "BIGINT NOT NULL AUTO_INCREMENT"
        else -> fail("Unsupported column type $columnType")
    }

    override fun primaryKey(name: String, vararg column: String): String = ", CONSTRAINT PK_$name PRIMARY KEY (${column.joinToString()})"
    override fun autoPrimaryKey(table: String, column: String): String = primaryKey(table, column)

    override fun createConnection(): DatabaseConnection {
        mysql.reloadSchema("test")
        return MySqlConnection.create("jdbc:mysql://localhost:${mysql.config.port}/test?useSSL=false", "user", "")
    }
}