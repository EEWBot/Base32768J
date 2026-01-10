package net.eewbot.base32768j;

import net.eewbot.base32768j.exception.Base32768Exception;
import org.junit.jupiter.api.Assertions;
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

    static List<String> failCaseProvider() {
        File baseDirectory = new File("src/test/resources/bad/");

        File[] files = baseDirectory.listFiles();
        if (files == null) throw new IllegalStateException("No test resources available.");

        List<String> testCases = Arrays.stream(files).<String>mapMulti((file, consumer) -> {
            String name = file.getName();
            int periodIndex = name.lastIndexOf('.');
            if (periodIndex <= 0) return;
            if (!name.substring(periodIndex + 1).equalsIgnoreCase("txt")) return;

            try {
                consumer.accept(Files.readString(file.toPath()));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toList();
        if (testCases.isEmpty()) throw new IllegalStateException("No test cases available.");

        return testCases;
    }

    @ParameterizedTest
    @MethodSource("successCaseProvider")
    void success(String testCase, byte[] expected) {
        byte[] actual = Base32768.getDecoder().decode(testCase);
        Assertions.assertArrayEquals(expected, actual);
    }

    static List<Arguments> successCaseProvider() throws IOException {
        File baseDirectory = new File("src/test/resources/pairs/");
        List<Util.TestCasePair> cases = Util.collectPairFiles(baseDirectory);

        if (cases.isEmpty()) throw new IllegalStateException("No test resources available.");

        List<Arguments> arguments = new ArrayList<>();
        for (Util.TestCasePair testCase : cases) {
            byte[] bytes = Files.readAllBytes(testCase.bin().toPath());
            String text = Files.readString(testCase.txt().toPath());
            arguments.add(Arguments.of(text, bytes));
        }

        return arguments;
    }
}
