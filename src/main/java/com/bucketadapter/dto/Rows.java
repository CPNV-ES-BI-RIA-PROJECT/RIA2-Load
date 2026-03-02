// src/main/java/com/bucketadapter/dto/Rows.java
package com.bucketadapter.dto;
// TODO Change with real data in week 5
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public final class Rows {
    private Rows() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerRow(
            @JsonProperty("customer_id") long customerId,
            @JsonProperty("email") String email,
            @JsonProperty("first_name") String firstName,
            @JsonProperty("last_name") String lastName,
            @JsonProperty("is_active") boolean isActive
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderRow(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("customer_id") long customerId,
            @JsonProperty("status") String status,
            @JsonProperty("total") BigDecimal total
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record OrderItemRow(
            @JsonProperty("order_id") String orderId,
            @JsonProperty("line_no") int lineNo,
            @JsonProperty("sku") String sku,
            @JsonProperty("qty") int qty,
            @JsonProperty("unit_price") BigDecimal unitPrice
    ) {}
}