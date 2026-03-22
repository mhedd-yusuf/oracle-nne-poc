package com.poc;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class OracleCrudApp implements CommandLineRunner {

    private final EmployeeRepository repo;

    public OracleCrudApp(EmployeeRepository repo) {
        this.repo = repo;
    }

    public static void main(String[] args) {
        SpringApplication.run(OracleCrudApp.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
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
    }
}
