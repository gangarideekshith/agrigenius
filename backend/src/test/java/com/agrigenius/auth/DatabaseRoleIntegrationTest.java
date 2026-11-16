package com.agrigenius.auth;

import com.agrigenius.support.PostgresTestcontainer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.agrigenius.integration.ml.MlServiceClient;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The least-privilege application role created by afterMigrate.sql can work with rows but
 * cannot change the schema or the migration history (decision D8).
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseRoleIntegrationTest implements PostgresTestcontainer {

    static final String APP_USER = "agrigenius_app_test";
    static final String APP_PASSWORD = "test-only-app-role-password";
    static final String INSUFFICIENT_PRIVILEGE = "42501";

    @MockitoBean
    MlServiceClient mlServiceClient;

    private Connection appConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), APP_USER, APP_PASSWORD);
    }

    @Test
    void appRoleIsNotSuperuser() throws Exception {
        try (Connection c = appConnection();
             PreparedStatement ps = c.prepareStatement(
                     "select rolsuper, rolcreaterole, rolcreatedb from pg_roles where rolname = ?")) {
            ps.setString(1, APP_USER);
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getBoolean(1)).isFalse();
                assertThat(rs.getBoolean(2)).isFalse();
                assertThat(rs.getBoolean(3)).isFalse();
            }
        }
    }

    @Test
    void appRoleCanReadAndWriteRows() throws Exception {
        try (Connection c = appConnection()) {
            c.setAutoCommit(false);
            try {
                UUID id = UUID.randomUUID();
                try (PreparedStatement insert = c.prepareStatement("""
                        insert into users (id, mobile_number, password_hash, role)
                        values (?, '+919000000001', '{bcrypt}x', 'FARMER')""")) {
                    insert.setObject(1, id);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
                try (PreparedStatement update = c.prepareStatement("update users set status = 'DISABLED' where id = ?")) {
                    update.setObject(1, id);
                    assertThat(update.executeUpdate()).isEqualTo(1);
                }
                try (PreparedStatement select = c.prepareStatement("select status from users where id = ?")) {
                    select.setObject(1, id);
                    try (ResultSet rs = select.executeQuery()) {
                        assertThat(rs.next()).isTrue();
                        assertThat(rs.getString(1)).isEqualTo("DISABLED");
                    }
                }
                try (PreparedStatement delete = c.prepareStatement("delete from users where id = ?")) {
                    delete.setObject(1, id);
                    assertThat(delete.executeUpdate()).isEqualTo(1);
                }
            } finally {
                c.rollback();
            }
        }
    }

    @Test
    void appRoleCannotCreateTables() {
        assertDenied("create table should_not_exist (id int)");
    }

    @Test
    void appRoleCannotDropTables() {
        assertDenied("drop table users");
    }

    @Test
    void appRoleCannotAlterTables() {
        assertDenied("alter table users add column hacked text");
    }

    @Test
    void appRoleCannotWriteMigrationHistory() {
        assertDenied("""
                insert into flyway_schema_history
                  (installed_rank, version, description, type, script, installed_by, execution_time, success)
                values (999, '999', 'x', 'SQL', 'x', 'x', 0, true)""");
    }

    private void assertDenied(String sql) {
        assertThatThrownBy(() -> {
            try (Connection c = appConnection(); Statement s = c.createStatement()) {
                s.execute(sql);
            }
        }).isInstanceOfSatisfying(SQLException.class,
                e -> assertThat(e.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE));
    }
}
