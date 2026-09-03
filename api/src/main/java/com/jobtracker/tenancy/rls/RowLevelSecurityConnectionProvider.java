package com.jobtracker.tenancy.rls;

import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Tells PostgreSQL which workspace a connection is working on, so V8's policies
 * have something to compare against.
 *
 * <p>This is the runtime half of phase 5e. The migration makes an unscoped query
 * return nothing; this is what stops every query being unscoped.
 *
 * <p>Hibernate's multi-tenancy SPI is the hook because it is the only one that
 * brackets a unit of work exactly: {@code getConnection} is called when a
 * session takes a connection and {@code releaseConnection} when it gives it
 * back, and the tenant arrives as an argument rather than being fished out of a
 * static field at some later moment.
 *
 * <p><b>Session scope plus an explicit reset, rather than SET LOCAL.</b> The
 * transaction-local form is the more obviously safe primitive - it expires at
 * COMMIT, so it cannot outlive the request that set it. It is not used here
 * because it is only local if a transaction has already begun, and this method
 * runs while Hibernate is acquiring the connection, before autocommit has
 * necessarily been turned off. A setting applied in autocommit mode is its own
 * one-statement transaction and is gone before the query it was meant to scope
 * ever runs - which fails closed, but fails closed on every request.
 *
 * <p>So the value is set for the session and cleared in {@code releaseConnection}.
 * That moves the burden onto the reset always happening, which is the leak this
 * whole phase exists to prevent, so it is asserted directly: see
 * RowLevelSecurityTest, which returns a connection to the pool and checks that
 * the next borrower sees nothing.
 */
@Component
public class RowLevelSecurityConnectionProvider implements MultiTenantConnectionProvider<String> {

    /** The GUC V8's policies read. Namespaced, or PostgreSQL rejects it. */
    private static final String WORKSPACE_SETTING = "app.workspace_id";

    /**
     * Parameterised, though the only values it can receive are produced by
     * WorkspaceTenantResolver. A setting name cannot be a bind parameter, hence
     * set_config() rather than SET - the same reason you cannot write
     * "SET ? = ?".
     */
    private static final String APPLY = "SELECT set_config(?, ?, false)";

    private final transient DataSource dataSource;

    public RowLevelSecurityConnectionProvider(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            apply(connection, tenantIdentifier);
        } catch (SQLException | RuntimeException e) {
            // A connection whose workspace could not be set is not safe to hand
            // out: it carries whatever the previous borrower left. Close it
            // rather than return it, and let the caller see the failure.
            connection.close();
            throw e;
        }
        return connection;
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        try {
            // Cleared before the connection goes back to the pool, because the
            // next borrower may be a different person entirely. An empty string
            // is not a number, so app_current_workspace() returns NULL and the
            // policies deny - a connection in the pool is scoped to nothing.
            apply(connection, "");
        } finally {
            connection.close();
        }
    }

    /**
     * Used by Hibernate outside any tenant - schema validation at startup, and
     * metadata lookups. Deliberately left unscoped: it reads catalogs, which
     * carry no workspace_id and no policy, and giving it a workspace would
     * suggest it had one.
     */
    @Override
    public Connection getAnyConnection() throws SQLException {
        return dataSource.getConnection();
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        connection.close();
    }

    /**
     * False: Hibernate keeps one connection for the whole transaction.
     *
     * <p>True would let it return the connection between statements and take
     * another later, which with a session-scoped setting means a statement could
     * run on a connection that has been reset. The setting and the connection
     * have to have the same lifetime.
     */
    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    private void apply(Connection connection, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(APPLY)) {
            statement.setString(1, WORKSPACE_SETTING);
            statement.setString(2, value);
            statement.execute();
        }
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return MultiTenantConnectionProvider.class.equals(unwrapType)
                || RowLevelSecurityConnectionProvider.class.equals(unwrapType);
    }

    @Override
    public <T> T unwrap(Class<T> unwrapType) {
        return isUnwrappableAs(unwrapType) ? unwrapType.cast(this) : null;
    }
}
