package com.load.service.sql;

import java.math.BigDecimal;

public final class SqlValueEncoder {
    private SqlValueEncoder() {}

    public static String v(String s) {
        if (s == null) return "NULL";
        return "'" + s.replace("'", "''") + "'";
    }

    public static String v(Boolean b) {
        if (b == null) return "NULL";
        return b ? "1" : "0";
    }

    public static String v(Number n) {
        if (n == null) return "NULL";
        return n.toString();
    }

    public static String v(BigDecimal n) {
        if (n == null) return "NULL";
        return n.toPlainString();
    }
}