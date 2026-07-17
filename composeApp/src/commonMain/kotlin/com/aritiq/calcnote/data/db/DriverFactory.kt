package com.aritiq.calcnote.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlSchema

/**
 * expect/actual seam for the SQLDelight driver. Android provides the actual via
 * [androidMain]; iOS later adds an iosMain actual using NativeSqliteDriver.
 */
expect class DriverFactory {
    fun createDriver(schema: SqlSchema<QueryResult.Value<Unit>>): SqlDriver
}