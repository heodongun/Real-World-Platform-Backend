package com.codingplatform.plugins

import com.codingplatform.database.DatabaseFactory
import io.ktor.server.application.Application

fun Application.configureDatabase(databaseFactory: DatabaseFactory) {
    // Database is initialised during ServiceRegistry creation.
    environment.log.info("Database connected: ${databaseFactory.database.url}")
}

