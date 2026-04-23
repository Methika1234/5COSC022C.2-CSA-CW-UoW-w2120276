package com.mycompany.new_cw.exception;

public class LinkedResourceNotFoundException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public LinkedResourceNotFoundException(String message) {
        super(message);
    }
}
