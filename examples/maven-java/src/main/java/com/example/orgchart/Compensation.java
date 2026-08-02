package com.example.orgchart;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Compensation package of an employee. A plain class whose non-static fields map to required
 * properties; {@code @JsonIgnore} on a field keeps it out of the generated schema.
 */
@JsonClassDescription("Compensation package of an employee.")
public class Compensation {

    @JsonPropertyDescription("Annual base salary in the smallest currency unit")
    private final long baseSalary;

    @JsonPropertyDescription("Annual bonus in the same currency unit")
    private final long annualBonus;

    @JsonPropertyDescription("ISO 4217 currency code")
    private final String currency;

    @JsonIgnore
    private final String bankAccountNumber;

    public Compensation(long baseSalary, long annualBonus, String currency, String bankAccountNumber) {
        this.baseSalary = baseSalary;
        this.annualBonus = annualBonus;
        this.currency = currency;
        this.bankAccountNumber = bankAccountNumber;
    }
}
