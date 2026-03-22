package com.poc;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * CRUD repository for the EMPLOYEES table.
 * Spring Boot auto-configures JdbcTemplate (from spring-boot-starter-jdbc)
 * and injects it here via constructor injection.
 */
@Repository
public class EmployeeRepository {

    private final JdbcTemplate jdbc;

    /* Maps a ResultSet row to an Employee — reused across all query methods. */
    private static final RowMapper<Employee> MAPPER = (rs, rowNum) -> {
        Employee e = new Employee();
        e.setId(rs.getLong("id"));
        e.setFirstName(rs.getString("first_name"));
        e.setLastName(rs.getString("last_name"));
        e.setEmail(rs.getString("email"));
        e.setSalary(rs.getDouble("salary"));
        e.setDepartment(rs.getString("department"));
        return e;
    };

    // Spring Boot auto-wires JdbcTemplate via constructor injection
    public EmployeeRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ── Schema ───────────────────────────────────────────────────────────────

    /**
     * Drops and recreates the EMPLOYEES table.
     * Uses an anonymous PL/SQL block for the drop so it works on Oracle 19c
     * (which does not support DROP TABLE IF EXISTS; that syntax is 23c+).
     */
    public void initTable() {
        // ORA-00942 = table or view does not exist — safe to ignore on first run
        jdbc.execute("""
                BEGIN
                    EXECUTE IMMEDIATE 'DROP TABLE employees PURGE';
                EXCEPTION
                    WHEN OTHERS THEN
                        IF SQLCODE = -942 THEN NULL; END IF;
                END;
                """);

        jdbc.execute("""
                CREATE TABLE employees (
                    id         NUMBER        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
                    first_name VARCHAR2(50)  NOT NULL,
                    last_name  VARCHAR2(50)  NOT NULL,
                    email      VARCHAR2(100) UNIQUE NOT NULL,
                    salary     NUMBER(10,2),
                    department VARCHAR2(50),
                    created_at TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    // ── CREATE ───────────────────────────────────────────────────────────────

    /**
     * Inserts an employee and returns the database-generated ID.
     * KeyHolder captures the IDENTITY column value returned by Oracle after the INSERT.
     */
    public long insert(Employee e) {
        String sql = """
                INSERT INTO employees (first_name, last_name, email, salary, department)
                VALUES (?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbc.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, e.getFirstName());
            ps.setString(2, e.getLastName());
            ps.setString(3, e.getEmail());
            ps.setDouble(4, e.getSalary());
            ps.setString(5, e.getDepartment());
            return ps;
        }, keyHolder);

        return Objects.requireNonNull(keyHolder.getKey(),
                "Generated key was null after INSERT").longValue();
    }

    // ── READ ─────────────────────────────────────────────────────────────────

    public List<Employee> findAll() {
        return jdbc.query(
                "SELECT * FROM employees ORDER BY id",
                MAPPER);
    }

    public Optional<Employee> findById(long id) {
        try {
            Employee e = jdbc.queryForObject(
                    "SELECT * FROM employees WHERE id = ?",
                    MAPPER, id);
            return Optional.ofNullable(e);
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    // ── UPDATE ───────────────────────────────────────────────────────────────

    public int updateSalary(long id, double newSalary) {
        return jdbc.update(
                "UPDATE employees SET salary = ? WHERE id = ?",
                newSalary, id);
    }

    // ── DELETE ───────────────────────────────────────────────────────────────

    public int delete(long id) {
        return jdbc.update(
                "DELETE FROM employees WHERE id = ?",
                id);
    }
}
