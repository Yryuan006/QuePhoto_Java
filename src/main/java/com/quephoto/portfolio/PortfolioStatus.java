package com.quephoto.portfolio;

public enum PortfolioStatus {
    DRAFT("draft"), PUBLISHED("published");
    private final String value;
    PortfolioStatus(String value) { this.value = value; }
    public String value() { return value; }
    public static PortfolioStatus parse(String value) {
        for (PortfolioStatus status : values()) {
            if (status.value.equals(value)) return status;
        }
        throw new IllegalArgumentException("status只接受draft或published");
    }
}