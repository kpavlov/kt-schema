package com.example.orgchart;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * Contact information of an employee: an email address, a phone number and a list of
 * known postal addresses (nested records).
 */
@JsonClassDescription("Contact information of an employee.")
public record ContactInfo(
        @JsonPropertyDescription("Primary email address") String email,
        @JsonPropertyDescription("Phone number in E.164 format") String phone,
        @JsonPropertyDescription("Known postal addresses of the employee") List<Address> addresses) {
}
