package com.poc;

public class Employee {

    private long   id;
    private String firstName;
    private String lastName;
    private String email;
    private double salary;
    private String department;

    public Employee() {}

    public Employee(String firstName, String lastName,
                    String email, double salary, String department) {
        this.firstName  = firstName;
        this.lastName   = lastName;
        this.email      = email;
        this.salary     = salary;
        this.department = department;
    }

    // ── Getters & Setters ────────────────────────────────────────────────────

    public long   getId()          { return id; }
    public void   setId(long id)   { this.id = id; }

    public String getFirstName()              { return firstName; }
    public void   setFirstName(String v)      { this.firstName = v; }

    public String getLastName()               { return lastName; }
    public void   setLastName(String v)       { this.lastName = v; }

    public String getEmail()                  { return email; }
    public void   setEmail(String v)          { this.email = v; }

    public double getSalary()                 { return salary; }
    public void   setSalary(double v)         { this.salary = v; }

    public String getDepartment()             { return department; }
    public void   setDepartment(String v)     { this.department = v; }

    @Override
    public String toString() {
        return "Employee{id=%d, name='%s %s', email='%s', salary=%.0f, dept='%s'}"
                .formatted(id, firstName, lastName, email, salary, department);
    }
}
