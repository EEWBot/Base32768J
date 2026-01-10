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

    record SuccessTestCase(byte[] bytes, String expected) {}
}
