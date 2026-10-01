package com.github.guoyyit.conditionalorderenginejava.dto;

/**
 * Request body for cancelling a conditional order or an ordinary order.
 */
public class CancelRequest {

    private String accountId;
    private Long expectedVersion;

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(Long expectedVersion) {
        this.expectedVersion = expectedVersion;
    }
}
