package net.eewbot.base32768j.exception;

/**
 * The error that the destination buffer is too small to write results.
 */
public class BufferTooSmallException extends Base32768Exception {
    public BufferTooSmallException(int expected, int actual) {
        super("Expected buffer length was " + expected + " or more, but actually " + actual + ".");
    }
}
