package com.poc;

import org.springframework.context.support.ClassPathXmlApplicationContext;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class OracleCrudApp {

    public static void main(String[] args) throws Exception {
        // Load bean definitions from applicationContext.xml (on the classpath).
        // Try-with-resources closes the context and triggers destroy-method="close"
        // on the dataSource bean when the app exits.
        try (ClassPathXmlApplicationContext ctx =
                     new ClassPathXmlApplicationContext("applicationContext.xml")) {

            DataSource dataSource = ctx.getBean("dataSource", DataSource.class);
            EmployeeRepository repo = ctx.getBean("employeeRepository", EmployeeRepository.class);

            // Hold a connection open for the lifetime of this block so the session
            // remains visible in V$SESSION while the sqlplus validation query is run.
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
}
