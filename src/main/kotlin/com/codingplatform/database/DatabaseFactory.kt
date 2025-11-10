package com.codingplatform.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.Closeable
import javax.sql.DataSource

class DatabaseFactory(
    private val config: DatabaseConfig,
    private val tables: List<org.jetbrains.exposed.sql.Table>
) : Closeable {
    private val dataSource: DataSource = createDataSource()
    val database: Database = Database.connect(dataSource)

    fun init() {
        transaction(database) {
            SchemaUtils.createMissingTablesAndColumns(*tables.toTypedArray())
        }
    }

    suspend fun <T> dbQuery(block: Transaction.() -> T): T =
        withContext(Dispatchers.IO) {
            transaction(database) {
                block(this)
            }
        }

    private fun createDataSource(): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            driverClassName = config.driver
            jdbcUrl = config.jdbcUrl
            username = config.username
            password = config.password
            maximumPoolSize = config.maxPoolSize
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }
        return HikariDataSource(hikariConfig)
    }

    override fun close() {
        TransactionManager.closeAndUnregister(database)
        if (dataSource is HikariDataSource) {
            dataSource.close()
        }
    }
}

data class DatabaseConfig(
    val jdbcUrl: String,
    val driver: String,
    val username: String,
    val password: String,
    val maxPoolSize: Int = 8
)
