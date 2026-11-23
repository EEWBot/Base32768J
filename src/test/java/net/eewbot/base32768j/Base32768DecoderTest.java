package net.eewbot.base32768j;

import net.eewbot.base32768j.exception.Base32768Exception;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Base32768DecoderTest {
    @ParameterizedTest
    @MethodSource("failCaseProvider")
    void fail(String testCase) {
        Assertions.assertThrows(Base32768Exception.class, () -> Base32768.getDecoder().decode(testCase));
    }

    static List<Arguments> failCaseProvider() {
        File baseDirectory = new File("src/test/resources/bad/");

        File[] files = baseDirectory.listFiles();
        if (files == null) throw new IllegalStateException("No test resources available.");

        List<Arguments> testCases = Arrays.stream(files).<Arguments>mapMulti((file, consumer) -> {
            String fullName = file.getName();

            int periodIndex = fullName.lastIndexOf('.');
            if (periodIndex <= 0) return;

            if (!fullName.substring(periodIndex + 1).equalsIgnoreCase("txt")) return;

            String beforeExtension = fullName.substring(0, periodIndex);

            try {
                consumer.accept(Arguments.of(Named.of(beforeExtension, Files.readString(file.toPath()))));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
        if (testCases.isEmpty()) throw new IllegalStateException("No test cases available.");

        return testCases;
    }

    @ParameterizedTest
    @MethodSource("successCaseProvider")
    void success(SuccessTestCase testCase) {
        byte[] actual = Base32768.getDecoder().decode(testCase.text);
        Assertions.assertArrayEquals(testCase.expected, actual);
    }

    static List<Arguments> successCaseProvider() throws IOException {
        File baseDirectory = new File("src/test/resources/pairs/");
        List<Util.TestCasePair> cases = Util.collectPairFiles(baseDirectory);

        if (cases.isEmpty()) throw new IllegalStateException("No test resources available.");

        List<Arguments> arguments = new ArrayList<>();
        for (Util.TestCasePair testCase : cases) {
            byte[] bytes = Files.readAllBytes(testCase.bin().toPath());
            String text = Files.readString(testCase.txt().toPath());
            arguments.add(Arguments.of(Named.of(testCase.name(), new SuccessTestCase(text, bytes))));
        }

        return arguments;
    }

    // Covers every decoder path boundary (Small Path, 8-char / 2-char fast paths, 7-bit and 15-bit tails).
    @org.junit.jupiter.api.Test
    void roundTripShortInputs() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(42);
        for (int len = 0; len <= 64; len++) {
            for (int trial = 0; trial < 20; trial++) {
                byte[] input = new byte[len];
                random.nextBytes(input);
                String encoded = Base32768.getEncoder().encodeToString(input);
                Assertions.assertArrayEquals(input, Base32768.getDecoder().decode(encoded), "length " + len);
            }
        }
    }

    // Corrupts valid encodings of every short length so that each decoder path (Small Path, 8-char block,
    // and the tail after it for every remainder length) must reject them.
    @org.junit.jupiter.api.Test
    void rejectsCorruptedShortInputs() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(7);
        char sevenBitChar = (char) Base32768Encoder.CODES_7_RANGES[0][0];
        for (int len = 1; len <= 64; len++) {
            byte[] input = new byte[len];
            random.nextBytes(input);
            char[] encoded = Base32768.getEncoder().encodeToString(input).toCharArray();
            int lastIndex = encoded.length - 1;

            // 1. Padding bits not all 1s: clear the lowest bit of the final character's value.
            int lastBits = isSevenBit(encoded[lastIndex]) ? 7 : 15;
            int padBits = (lastIndex * 15 + lastBits) & 7;
            if (padBits != 0) {
                char[] bad = encoded.clone();
                bad[lastIndex] = (char) (bad[lastIndex] & ~1); // value's lowest bit == code point's lowest bit
                assertRejected(bad, "bad padding, length " + len);
            }

            // 2. A 7-bit character and 3. an invalid character at every non-final position.
            for (int pos = 0; pos < lastIndex; pos++) {
                char[] bad7 = encoded.clone();
                bad7[pos] = sevenBitChar;
                assertRejected(bad7, "7-bit char at " + pos + ", length " + len);

                char[] badChar = encoded.clone();
                badChar[pos] = '�';
                assertRejected(badChar, "invalid char at " + pos + ", length " + len);
            }
        }
    }

    private static boolean isSevenBit(char c) {
        for (int[] range : Base32768Encoder.CODES_7_RANGES) {
            if (c >= range[0] && c <= range[1]) return true;
        }
        return false;
    }

    private static void assertRejected(char[] text, String message) {
        Assertions.assertThrows(net.eewbot.base32768j.exception.IllegalBase32768TextException.class,
            () -> Base32768.getDecoder().decode(new String(text)), message);
    }

    @org.junit.jupiter.api.Test
    void decodeByteBufferAdvancesPositionOnlyOnSuccess() {
        byte[] payload = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17};
        byte[] encoded = Base32768.getEncoder().encode(payload);
        byte[] prefix = {42, 42, 42};

        // Valid input after a non-zero position: decodes only the remaining bytes, then position == limit.
        java.nio.ByteBuffer ok = java.nio.ByteBuffer.allocate(prefix.length + encoded.length);
        ok.put(prefix).put(encoded).position(prefix.length);
        java.nio.ByteBuffer result = Base32768.getDecoder().decode(ok);
        Assertions.assertEquals(ok.limit(), ok.position());
        Assertions.assertEquals(0, result.position());
        byte[] actual = new byte[result.remaining()];
        result.get(actual);
        Assertions.assertArrayEquals(payload, actual);

        // Invalid input: the exception is thrown and the position is not advanced.
        byte[] bad = "\u0000".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        java.nio.ByteBuffer ng = java.nio.ByteBuffer.allocate(prefix.length + bad.length);
        ng.put(prefix).put(bad).position(prefix.length);
        Assertions.assertThrows(Base32768Exception.class, () -> Base32768.getDecoder().decode(ng));
        Assertions.assertEquals(prefix.length, ng.position());
    }

    // decode(byte[], byte[]) decodes straight into dst and decode(ByteBuffer) reads the backing array directly.
    @org.junit.jupiter.api.Test
    void byteApisMatchDecodeString() {
        java.util.SplittableRandom random = new java.util.SplittableRandom(11);
        Base32768Decoder decoder = Base32768.getDecoder();
        for (int len = 0; len <= 64; len++) {
            for (int trial = 0; trial < 10; trial++) {
                byte[] input = new byte[len];
                random.nextBytes(input);
                byte[] encoded = Base32768.getEncoder().encode(input);
                String message = "length " + len + ", trial " + trial;

                Assertions.assertArrayEquals(input, decoder.decode(encoded), message);

                byte[] dst = new byte[len + 20];
                java.util.Arrays.fill(dst, (byte) 0x5A);
                Assertions.assertEquals(len, decoder.decode(encoded, dst), message);
                Assertions.assertArrayEquals(input, java.util.Arrays.copyOf(dst, len), message);
                for (int k = len; k < dst.length; k++) Assertions.assertEquals((byte) 0x5A, dst[k], message);

                if (len > 0) {
                    byte[] small = new byte[len - 1];
                    Assertions.assertThrows(net.eewbot.base32768j.exception.BufferTooSmallException.class,
                        () -> decoder.decode(encoded, small), message);
                    for (byte b : small) Assertions.assertEquals(0, b, message);
                }

                byte[] backing = new byte[encoded.length + 6];
                System.arraycopy(encoded, 0, backing, 4, encoded.length);
                java.nio.ByteBuffer sliced = java.nio.ByteBuffer.wrap(backing, 2, encoded.length + 2).slice();
                sliced.position(2);
                java.nio.ByteBuffer result = decoder.decode(sliced);
                Assertions.assertEquals(sliced.limit(), sliced.position(), message);
                byte[] actual = new byte[result.remaining()];
                result.get(actual);
                Assertions.assertArrayEquals(input, actual, message);

                java.nio.ByteBuffer direct = java.nio.ByteBuffer.allocateDirect(encoded.length).put(encoded).flip();
                result = decoder.decode(direct);
                Assertions.assertEquals(direct.limit(), direct.position(), message);
                actual = new byte[result.remaining()];
                result.get(actual);
                Assertions.assertArrayEquals(input, actual, message);
            }
        }
    }

    @org.junit.jupiter.api.Test
    void outputLengthDoesNotOverflowForHugeInputs() {
        // (n - 1) * 15 overflows int here, and the old int formula gave a wrong length beyond ~286M chars.
        for (int n : new int[] {1, 2, 143_165_577, 300_000_000, 1_073_741_823}) {
            for (int lastBits : new int[] {7, 15}) {
                long expected = ((long) (n - 1) * 15 + lastBits) / 8;
                Assertions.assertEquals(expected, Base32768Decoder.outputLength(n, lastBits), "n=" + n + ", lastBits=" + lastBits);
            }
        }
    }

    record SuccessTestCase(String text, byte[] expected) {}
}
