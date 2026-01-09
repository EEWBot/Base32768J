package net.eewbot.base32768j.exception;

/**
 * The error that couldn't decode input because it's not in valid Base32768 scheme.
 */
public class IllegalBase32768TextException extends Base32768Exception {
    public IllegalBase32768TextException(int at, int codePoint) {
        super("Unknown code point at " + at + ": " + codePoint);
    }

    public IllegalBase32768TextException(int codePoint) {
        super("Unexpected 7-bits-per-char code point at last character: " + codePoint);
    }

    public IllegalBase32768TextException(String message) {
        super(message);
    }
}
