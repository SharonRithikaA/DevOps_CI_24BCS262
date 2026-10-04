package com.gymms.exception;

public class MemberNotFoundException extends ResourceNotFoundException {
    public MemberNotFoundException(Long id) {
        super("Member not found with id " + id);
    }
}
