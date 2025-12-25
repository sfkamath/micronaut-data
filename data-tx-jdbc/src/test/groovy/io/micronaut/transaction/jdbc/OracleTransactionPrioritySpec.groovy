package io.micronaut.transaction.jdbc

import io.micronaut.data.connection.ConnectionDefinition
import io.micronaut.data.connection.annotation.TransactionPriority
import io.micronaut.data.connection.support.DefaultConnectionStatus
import io.micronaut.data.connection.DefaultConnectionDefinition
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.impl.DefaultTransactionStatus
import spock.lang.Specification

import javax.sql.DataSource
import java.sql.Connection
import java.sql.DatabaseMetaData
import java.sql.Statement

/**
 * Verifies that Oracle transaction priority is applied (ALTER SESSION "txn_priority")
 * at transaction begin and reset after completion when @TransactionPriority is present.
 */
class OracleTransactionPrioritySpec extends Specification {

    def "applies and resets Oracle txn_priority when @TransactionPriority present"() {
        given: "Mocks for Oracle connection and statement"
        def dataSource = Mock(DataSource)
        def connection = Mock(Connection)
        def meta = Mock(DatabaseMetaData)
        def stmt = Mock(Statement)

        and: "Connection definition carrying that annotation metadata"
        ConnectionDefinition connDef = new DefaultConnectionDefinition("test")

        and: "Connection status wrapping the connection and allowing synchronizations"
        def status = new DefaultConnectionStatus<>(connection, connDef, true, null)

        and: "A DefaultTransactionStatus with the above connection status"
        def txDef = createWithPriority(TransactionPriority.Level.LOW)
        def txManager = new DataSourceTransactionManager(dataSource, Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager))

        def txStatus = DefaultTransactionStatus.newTx(status, txDef, txManager)

        and: "Oracle connection behavior"
        connection.getMetaData() >> meta
        meta.getDatabaseProductName() >> "Oracle"

        // doBegin path adjusts JDBC settings; allow these calls
        connection.getAutoCommit() >> true
        connection.setAutoCommit(false) >> { }
        connection.isReadOnly() >> false
        connection.getTransactionIsolation() >> Connection.TRANSACTION_READ_COMMITTED

        // createStatement used twice: set LOW, then reset to HIGH on completion
        2 * connection.createStatement() >> stmt
        // try-with-resources closes the statement; ignore close calls
        _ * stmt.close()

        when: "Beginning the transaction"
        txManager.doBegin(txStatus)

        then: "Priority is set to LOW"
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="LOW"')

        when: "Execution completes (triggers onComplete reset)"
        status.complete() // triggers ConnectionSynchronization.executionComplete

        then: "Priority is reset to HIGH"
        1 * stmt.executeUpdate('ALTER SESSION SET "txn_priority"="HIGH"')
    }

    def "no priority applied for non-Oracle databases"() {
        given:
        def dataSource = Mock(DataSource)
        def connection = Mock(Connection)
        def meta = Mock(DatabaseMetaData)


        ConnectionDefinition connDef = new DefaultConnectionDefinition("test")

        def status = new DefaultConnectionStatus<>(connection, connDef, true, null)
        def txDef = createWithPriority(TransactionPriority.Level.HIGH)
        def txManager = new DataSourceTransactionManager(dataSource, Mock(io.micronaut.data.connection.ConnectionOperations), Mock(io.micronaut.data.connection.SynchronousConnectionManager))
        def txStatus = DefaultTransactionStatus.newTx(status, txDef, txManager)

        and: "Non-Oracle database"
        connection.getMetaData() >> meta
        meta.getDatabaseProductName() >> "H2"

        // doBegin path adjusts JDBC settings; allow these calls
        connection.getAutoCommit() >> true
        connection.setAutoCommit(false) >> { }
        connection.isReadOnly() >> false
        connection.getTransactionIsolation() >> Connection.TRANSACTION_READ_COMMITTED

        when:
        txManager.doBegin(txStatus)
        status.complete()

        then: "No ALTER SESSION is executed"
        0 * connection.createStatement()
    }

    static TransactionDefinition createWithPriority(TransactionPriority.Level priority) {
        return new TransactionDefinition() {

            @Override
            public String getName() {
                return "DEFAULT";
            }

            @Override
            TransactionPriority.Level getPriority() {
                return priority
            }
        }
    }
}
