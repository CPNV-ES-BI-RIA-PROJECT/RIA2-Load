package com.load.service;

import com.load.dto.TestPayload;
import com.load.dto.Rows.*;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
// TODO Change with real data in week 5
@Service
public class TestPayloadReader {

    private final JsonMapper jsonMapper;

    public TestPayloadReader(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public TestPayload read(byte[] bytes) {
        return jsonMapper.readValue(bytes, TestPayload.class);
    }

    public List<CustomerRow> asCustomers(TestPayload.TablePayload table) {
        return table.rows().stream()
                .map(node -> jsonMapper.treeToValue(node, CustomerRow.class))
                .toList();
    }

    public List<OrderRow> asOrders(TestPayload.TablePayload table) {
        return table.rows().stream()
                .map(node -> jsonMapper.treeToValue(node, OrderRow.class))
                .toList();
    }

    public List<OrderItemRow> asOrderItems(TestPayload.TablePayload table) {
        return table.rows().stream()
                .map(node -> jsonMapper.treeToValue(node, OrderItemRow.class))
                .toList();
    }
}