package com.poc;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

@SpringBootApplication
public class OracleCrudApp implements CommandLineRunner {

    private final DataSource dataSource;
    private final EmployeeRepository repo;

    public OracleCrudApp(DataSource dataSource, EmployeeRepository repo) {
        this.dataSource = dataSource;
        this.repo       = repo;
    }

    public static void main(String[] args) {
        SpringApplication.run(OracleCrudApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // Hold a connection open for the lifetime of this method so the session
        // remains visible in V$SESSION while the sqlplus validation query is run
        try (Connection conn = dataSource.getConnection()) {

            repo.initTable();

            repo.insert(new Employee("Alice", "Johnson",  "alice.j@example.com",  90000, "Engineering"));
            repo.insert(new Employee("Bob",   "Williams", "bob.w@example.com",    65000, "Finance"));
            repo.insert(new Employee("Carol", "Brown",    "carol.b@example.com",  78000, "HR"));

            System.out.println("Connected and inserted 3 employees:");
            List<Employee> employees = repo.findAll();
            for (Employee e : employees) {
                System.out.printf("  [%d] %s %s — %s%n",
                        e.getId(), e.getFirstName(), e.getLastName(), e.getDepartment());
            }

            // Self-check: query this connection's own NNE status directly
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                     "SELECT " +
                     "MAX(CASE WHEN network_service_banner LIKE '%Encryption service adapter%'        THEN 'YES' ELSE 'NO' END) AS encrypted, " +
                     "MAX(CASE WHEN network_service_banner LIKE '%Crypto-checksumming service adapter%' THEN 'YES' ELSE 'NO' END) AS checksum " +
                     "FROM v$session_connect_info WHERE sid = SYS_CONTEXT('USERENV','SID')")) {
                rs.next();
                System.out.println("\nNNE — ENCRYPTED: " + rs.getString("encrypted") +
                                   ", CHECKSUM: " + rs.getString("checksum"));
            }

            System.out.println("\nConnection is open — run the sqlplus validation query now.");
            System.out.println("Press ENTER to exit.");
            System.in.read();
        }
    }
}
