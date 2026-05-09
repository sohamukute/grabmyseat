package com.grabmyseat.pay;

public class PaymentException extends RuntimeException {

    public PaymentException(String message) {
        super(message);
    }
}
