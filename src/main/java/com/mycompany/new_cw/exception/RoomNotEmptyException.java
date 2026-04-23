package com.mycompany.new_cw.exception;

public class RoomNotEmptyException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public RoomNotEmptyException(String message) {
        super(message);
    }
}
