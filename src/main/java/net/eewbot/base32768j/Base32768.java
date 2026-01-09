package net.eewbot.base32768j;

/**
 * This class consists exclusively of static methods for obtaining encoders and decoders for the Base32768 encoding
 * scheme.
 * The implementation of this class follows the
 * <a href="https://github.com/qntm/base32768">original Base32768</a>
 */
public class Base32768 {
    private static final Base32768Encoder encoder = new Base32768Encoder();
    private static final Base32768Decoder decoder = new Base32768Decoder();

    /**
     * Returns a {@link Base32768Encoder}.
     * @return A base32768 encoder.
     */
    public static Base32768Encoder getEncoder() {
        return encoder;
    }

    /**
     * Returns a {@link Base32768Decoder}.
     * @return A base32768 decoder.
     */
    public static Base32768Decoder getDecoder() {
        return decoder;
    }
}
