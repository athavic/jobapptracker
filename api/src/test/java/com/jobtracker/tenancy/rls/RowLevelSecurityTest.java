package com.jobtracker.tenancy.rls;

import com.jobtracker.auth.ServiceKeyAuthenticationFilter;
import com.jobtracker.auth.WorkspaceUser;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The database refuses to serve one workspace's rows to another, on its own.
 *
 * <p>WorkspaceScopingTest already proves the service layer scopes its queries.
 * This proves the thing underneath: that a query which forgets returns nothing.
 * The two are not the same claim, and only this one survives somebody adding an
 * endpoint without reading either test.
 *
 * <p><b>Why this does not use @SpringBootTest.</b> Testcontainers hands the
 * application the database's own superuser, and PostgreSQL exempts superusers
 * from every policy. A test that went through the usual Spring beans would pass
 * against a database with no policies at all, which makes it worse than no test
 * - it would report success for exactly the mistake 5e exists to prevent. So
 * this connects as jobtracker_app, the unprivileged role V8 creates, which is
 * the role the real application uses.
 */
@Testcontainers
class RowLevelSecurityTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    /** Matches the default in application.yml, which V8 writes into the role. */
    private static final String APP_PASSWORD = "jobtracker_app";

    private static HikariDataSource asAppRole;

    private static final long WORKSPACE_A = 1L;
    private static long workspaceB;

    @BeforeAll
    static void migrateAndSeed() throws SQLException {
        // As the owner, exactly as Flyway runs in production.
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .placeholders(Map.of("appDbPassword", APP_PASSWORD))
                .load()
                .migrate();

        try (Connection owner = ownerConnection(); Statement st = owner.createStatement()) {
            // Seeded as the owner on purpose: the owner is not filtered, which
            // is what lets a migration backfill. If this insert were subject to
            // the policies the fixture could not be built at all.
            st.execute("INSERT INTO workspace (name) VALUES ('Second') RETURNING id");
            try (ResultSet rs = st.executeQuery("SELECT id FROM workspace WHERE name = 'Second'")) {
                rs.next();
                workspaceB = rs.getLong(1);
            }
            st.execute("INSERT INTO company (workspace_id, name) VALUES "
                    + "(" + WORKSPACE_A + ", 'Stripe'), (" + workspaceB + ", 'Stripe')");
            st.execute("INSERT INTO job_application "
                    + "(workspace_id, company_id, role_title, status, applied_at) "
                    + "SELECT workspace_id, id, 'engineer', 'APPLIED', now() FROM company");
        }

        // One connection in the pool, so every borrow in this test is the same
        // physical connection. That is what makes the leak test meaningful.
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(POSTGRES.getJdbcUrl());
        config.setUsername("jobtracker_app");
        config.setPassword(APP_PASSWORD);
        config.setMaximumPoolSize(1);
        asAppRole = new HikariDataSource(config);
    }

    @AfterAll
    static void closePool() {
        if (asAppRole != null) {
            asAppRole.close();
        }
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("a connection that names no workspace reads nothing, not everything")
    void failsClosed() throws SQLException {
        try (Connection connection = asAppRole.getConnection()) {
            assertThat(countApplications(connection))
                    .as("an unscoped query must return zero rows, which is what makes "
                            + "a forgotten scope a visible bug rather than a silent breach")
                    .isZero();
        }
    }

    @Test
    @DisplayName("a workspace sees its own rows and only its own")
    void scopedToOneWorkspace() throws SQLException {
        assertThat(countAs(String.valueOf(WORKSPACE_A))).isEqualTo(1);
        assertThat(countAs(String.valueOf(workspaceB))).isEqualTo(1);

        try (Connection connection = asAppRole.getConnection()) {
            setWorkspace(connection, String.valueOf(WORKSPACE_A));
            try (Statement st = connection.createStatement();
                 ResultSet rs = st.executeQuery("SELECT workspace_id FROM job_application")) {
                rs.next();
                assertThat(rs.getLong(1)).isEqualTo(WORKSPACE_A);
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("the worker's sentinel reads every workspace")
    void workerReadsAcross() throws SQLException {
        assertThat(countAs(WorkspaceTenantResolver.ALL_WORKSPACES)).isEqualTo(2);
    }

    @Test
    @DisplayName("a value that is not a number denies, rather than erroring")
    void nonsenseDenies() throws SQLException {
        assertThat(countAs(WorkspaceTenantResolver.NO_WORKSPACE)).isZero();
        assertThat(countAs("'; drop table job_application; --")).isZero();
    }

    @Test
    @DisplayName("a workspace cannot write a row into another workspace")
    void cannotWriteAcross() throws SQLException {
        try (Connection connection = asAppRole.getConnection()) {
            setWorkspace(connection, String.valueOf(WORKSPACE_A));
            assertThatThrownBy(() -> {
                try (Statement st = connection.createStatement()) {
                    st.execute("INSERT INTO company (workspace_id, name) VALUES ("
                            + workspaceB + ", 'Smuggled')");
                }
            })
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @DisplayName("the worker may read everywhere but still may not write unscoped")
    void workerCannotWriteUnscoped() throws SQLException {
        try (Connection connection = asAppRole.getConnection()) {
            setWorkspace(connection, WorkspaceTenantResolver.ALL_WORKSPACES);
            assertThatThrownBy(() -> {
                try (Statement st = connection.createStatement()) {
                    st.execute("INSERT INTO company (workspace_id, name) VALUES ("
                            + workspaceB + ", 'By the worker')");
                }
            })
                    .as("USING accepts the sentinel, WITH CHECK must not: "
                            + "'write into all of them' is not a coherent request")
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("row-level security");
        }
    }

    @Test
    @DisplayName("the application role cannot switch row-level security off")
    void cannotDisablePolicies() throws SQLException {
        try (Connection connection = asAppRole.getConnection();
             Statement st = connection.createStatement()) {
            assertThatThrownBy(() -> st.execute("ALTER TABLE job_application DISABLE ROW LEVEL SECURITY"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("must be owner");
        }
    }

    /**
     * The failure this whole design is exposed to.
     *
     * <p>The workspace is set for the session rather than the transaction - see
     * RowLevelSecurityConnectionProvider for why - so correctness depends on the
     * reset running before the connection goes back to the pool. The pool here
     * holds exactly one connection, so the second borrow is guaranteed to be the
     * same physical connection as the first.
     */
    @Test
    @DisplayName("a connection returned to the pool carries no workspace to the next borrower")
    void doesNotLeakThroughThePool() throws SQLException {
        RowLevelSecurityConnectionProvider provider =
                new RowLevelSecurityConnectionProvider(asAppRole);

        Connection first = provider.getConnection(String.valueOf(WORKSPACE_A));
        assertThat(countApplications(first)).isEqualTo(1);
        provider.releaseConnection(String.valueOf(WORKSPACE_A), first);

        try (Connection second = asAppRole.getConnection()) {
            assertThat(countApplications(second))
                    .as("the next borrower of this physical connection must inherit nothing")
                    .isZero();
        }
    }

    @Test
    @DisplayName("the resolver answers by what a caller holds, never by elimination")
    void resolverIdentifiesCallersPositively() {
        WorkspaceTenantResolver resolver = new WorkspaceTenantResolver();

        assertThat(resolver.resolveCurrentTenantIdentifier())
                .as("nobody signed in must not mean every workspace")
                .isEqualTo(WorkspaceTenantResolver.NO_WORKSPACE);

        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("automation", null,
                        List.of(new SimpleGrantedAuthority(ServiceKeyAuthenticationFilter.ROLE_SERVICE))));
        assertThat(resolver.resolveCurrentTenantIdentifier())
                .isEqualTo(WorkspaceTenantResolver.ALL_WORKSPACES);

        SecurityContextHolder.clearContext();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("someone-else", null, List.of()));
        assertThat(resolver.resolveCurrentTenantIdentifier())
                .as("an authenticated principal that is neither a member nor the worker "
                        + "is entitled to nothing")
                .isEqualTo(WorkspaceTenantResolver.NO_WORKSPACE);
    }

    // ---------------------------------------------------------------------

    private static Connection ownerConnection() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private int countAs(String tenant) throws SQLException {
        try (Connection connection = asAppRole.getConnection()) {
            setWorkspace(connection, tenant);
            return countApplications(connection);
        }
    }

    private static void setWorkspace(Connection connection, String tenant) throws SQLException {
        try (var st = connection.prepareStatement("SELECT set_config('app.workspace_id', ?, false)")) {
            st.setString(1, tenant);
            st.execute();
        }
    }

    private static int countApplications(Connection connection) throws SQLException {
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM job_application")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
