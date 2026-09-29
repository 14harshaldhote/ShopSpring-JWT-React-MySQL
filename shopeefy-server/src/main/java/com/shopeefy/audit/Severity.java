package com.shopeefy.audit;

public enum Severity {
    INFO, WARN, HIGH, CRITICAL;

    public boolean alertsAdmin() {
        return this == HIGH || this == CRITICAL;
    }
}
