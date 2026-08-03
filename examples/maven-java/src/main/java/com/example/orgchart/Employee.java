package com.example.orgchart;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An employee of the organization. The {@code manager}/{@code reports} accessors make the
 * resulting schema recursive: {@code Employee} references itself both directly and via a
 * collection, so the generated {@code $defs} entry points back at the root schema.
 */
@JsonClassDescription("An employee of the organization.")
public interface Employee {

    @JsonProperty("employee_id")
    @JsonPropertyDescription("Unique identifier of the employee")
    String getEmployeeId();

    @JsonPropertyDescription("Full name of the employee")
    String getName();

    @JsonPropertyDescription("Whether the employee is currently active")
    boolean isActive();

    @JsonPropertyDescription("Contact details of the employee")
    ContactInfo getContactInfo();

    @JsonPropertyDescription("Compensation package of the employee")
    Compensation getCompensation();

    @JsonPropertyDescription("Employment type of the employee")
    EmploymentType getEmploymentType();

    @JsonPropertyDescription("Department the employee belongs to")
    @Nullable
    Department getDepartment();

    @JsonPropertyDescription("Direct reporting manager of the employee")
    @Nullable
    Employee getManager();

    @JsonPropertyDescription("Employees directly reporting to this employee")
    List<Employee> getReports();

    @JsonPropertyDescription("Skills of the employee")
    Set<String> getSkills();

    @JsonPropertyDescription("Free-form employee attributes")
    Map<String, String> getAttributes();
}
