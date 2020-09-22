package com.martmists.hackgame.server.database

import com.google.common.collect.Queues
import com.martmists.hackgame.server.Server
import com.martmists.hackgame.server.database.tables.AccountTable
import com.martmists.hackgame.server.database.tables.HostTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.transaction as dbTransaction
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread

object DatabaseManager {
    val dbUser = System.getProperty("hackgame.database.username", "hackgame")
    val dbPassword = System.getProperty("hackgame.database.password", "hackgame")
    val dbName = System.getProperty("hackgame.database.dbname", "hackgame")
    val dbHost = System.getProperty("hackgame.database.host", "localhost")
    val dbPort = System.getProperty("hackgame.database.port", "5432").toInt()

    @Volatile
    var running = true
    val queue = ConcurrentLinkedQueue<Pair<CompletableFuture<Any>, Transaction.() -> Any>>()

    val database = Database.connect("jdbc:postgresql://$dbHost:$dbPort/$dbName", driver="org.postgresql.Driver", user=dbUser, password=dbPassword)
    val dbThread = thread(start=true, isDaemon=true, name="HackGame Database Thread") {
        while (running) {

            val pair = queue.poll()

            if (pair == null) {
                Thread.sleep(20)  // 20ms to not lag thread
                continue
            }

            try {
                val value = dbTransaction(database) {  // Renamed because recursion
                    val x = pair.second.invoke(this)
                    x
                }

                pair.first.complete(value)
            } catch(e: Exception) {
                Server.INSTANCE.LOGGER.error("Error on Database Thread!", e)
                pair.first.completeExceptionally(e)
            }
        }
    }

    fun registerDefaultTables() {
        transaction {
            SchemaUtils.createMissingTablesAndColumns(tables=arrayOf(AccountTable, HostTable))
        }
    }

    fun <T> transaction(callback: Transaction.() -> T): CompletableFuture<T> {
        val fut = CompletableFuture<T>()
        queue.add(Pair(fut as CompletableFuture<Any>, callback as Transaction.() -> Any))
        return fut
    }
}