package net.eewbot.base32768j;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class Base32768EncoderTest {
    @ParameterizedTest
    @MethodSource("successCaseProvider")
    void success(SuccessTestCase testCase) {
        String actual = Base32768.getEncoder().encodeToString(testCase.bytes);
        Assertions.assertEquals(testCase.expected, actual);
    }

    static List<Arguments> successCaseProvider() throws IOException {
        File baseDirectory = new File("src/test/resources/pairs/");
        List<Util.TestCasePair> cases = Util.collectPairFiles(baseDirectory);

        if (cases.isEmpty()) throw new IllegalStateException("No test resources available.");

        List<Arguments> arguments = new ArrayList<>();
        for (Util.TestCasePair testCase : cases) {
            byte[] bytes = Files.readAllBytes(testCase.bin().toPath());
            String text = Files.readString(testCase.txt().toPath());
            arguments.add(Arguments.of(Named.of(testCase.name(), new SuccessTestCase(bytes, text))));
        }

        return arguments;
    }

    // encode(byte[]) writes UTF-8 directly; it must match the String route for every length and for inputs that
    // produce 2-byte UTF-8 chars (all-zero bytes map to U+04A0) as well as 3-byte ones.
    @org.junit.jupiter.api.Test
    void byteApisMatchEncodeToString() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(3);
        for (int len = 0; len <= 64; len++) {
            for (int trial = 0; trial < 22; trial++) {
                byte[] input = new byte[len];
                if (trial == 1) java.util.Arrays.fill(input, (byte) 0xFF);
                else if (trial >= 2) random.nextBytes(input);
                if (trial >= 2 && trial % 2 == 0) {
                    for (int k = 0; k < len; k++) if (random.nextInt(3) != 0) input[k] = 0;
                }
                assertByteApis(input, "length " + len + ", trial " + trial);
            }
        }
    }

    private static void assertByteApis(byte[] input, String message) {
        Base32768Encoder encoder = Base32768.getEncoder();
        byte[] expected = encoder.encodeToString(input).getBytes(java.nio.charset.StandardCharsets.UTF_8);

        Assertions.assertArrayEquals(expected, encoder.encode(input), message);

        // encode(byte[], byte[]): exact-size and larger dst; bytes past the result stay untouched.
        byte[] dst = new byte[expected.length + 5];
        java.util.Arrays.fill(dst, (byte) 0x5A);
        Assertions.assertEquals(expected.length, encoder.encode(input, dst), message);
        Assertions.assertArrayEquals(expected, java.util.Arrays.copyOf(dst, expected.length), message);
        for (int k = expected.length; k < dst.length; k++) Assertions.assertEquals((byte) 0x5A, dst[k], message);

        if (expected.length > 0) {
            byte[] small = new byte[expected.length - 1];
            Assertions.assertThrows(net.eewbot.base32768j.exception.BufferTooSmallException.class,
                () -> encoder.encode(input, small), message);
            for (byte b : small) Assertions.assertEquals(0, b, message);
        }

        // encode(ByteBuffer): heap buffer with a non-zero arrayOffset and position, and a direct buffer.
        byte[] backing = new byte[input.length + 6];
        System.arraycopy(input, 0, backing, 4, input.length);
        java.nio.ByteBuffer sliced = java.nio.ByteBuffer.wrap(backing, 2, input.length + 2).slice();
        sliced.position(2);
        assertBufferResult(expected, encoder.encode(sliced), message);
        Assertions.assertEquals(sliced.limit(), sliced.position(), message);

        java.nio.ByteBuffer direct = java.nio.ByteBuffer.allocateDirect(input.length).put(input).flip();
        assertBufferResult(expected, encoder.encode(direct), message);
        Assertions.assertEquals(direct.limit(), direct.position(), message);
    }

    private static void assertBufferResult(byte[] expected, java.nio.ByteBuffer result, String message) {
        Assertions.assertEquals(0, result.position(), message);
        byte[] actual = new byte[result.remaining()];
        result.get(actual);
        Assertions.assertArrayEquals(expected, actual, message);
    }

    record SuccessTestCase(byte[] bytes, String expected) {}
}
