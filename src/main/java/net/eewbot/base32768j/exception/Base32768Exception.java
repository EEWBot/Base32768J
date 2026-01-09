package net.eewbot.base32768j.exception;

/**
 * The base class of errors in Base32768J.
 */
public abstract class Base32768Exception extends RuntimeException {
    public Base32768Exception(String message) {
        super(message);
    }
}
