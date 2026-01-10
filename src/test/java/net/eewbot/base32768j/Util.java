package net.eewbot.base32768j;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Util {
    public static List<File> collectAllFiles(File baseDirectory) {
        File[] files = baseDirectory.listFiles();
        if (files == null) return Collections.emptyList();

        ArrayList<File> list = new ArrayList<>();

        for (File file : files) {
            if (file.isDirectory()) {
                list.addAll(collectAllFiles(file));
                continue;
            }
            list.add(file);
        }

        return list;
    }

    public static List<TestCasePair> collectPairFiles(File baseDirectory) {
        List<File> files = collectAllFiles(baseDirectory);

        List<File> binaries = files.parallelStream().filter(file -> {
            String name = file.getName();

            int periodIndex = name.lastIndexOf('.');
            if (periodIndex <= 0) return false;

            String extension = name.substring(periodIndex + 1);
            return extension.equalsIgnoreCase("bin");
        }).toList();

        return binaries.stream().<TestCasePair>mapMulti((bin, consumer) -> {
            String fullName = bin.getName();
            int periodIndex = fullName.lastIndexOf('.');
            String beforeExtension = fullName.substring(0, periodIndex);

            final File[] text = {null};
            files.removeIf(file -> {
                if (!file.getName().equalsIgnoreCase(beforeExtension + ".txt")) return false;
                text[0] = file;
                return true;
            });

            if (text[0] == null) return;
            consumer.accept(new TestCasePair(bin, text[0]));
        }).toList();
    }

    public record TestCasePair(File bin, File txt) {}
}
