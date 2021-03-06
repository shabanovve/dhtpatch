package ru.dz.dhtpatch;

import lombok.extern.java.Log;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Created by Vladimir Shabanov on 09/02/16.
 */
@Log
public class Patcher {

    public void start() {
        try {
            Path path = FileUtils.findFile(Constant.FILE_NAME);
            if (isFilePatched(path)) {
                log.info("File already is patched");
                revertFromBackup(path);
            } else {
                makePatch(path);
            }
        } catch (IOException | URISyntaxException e) {
            log.severe(e.getMessage());
        }

    }

    public void makePatch(Path path) throws IOException {
        new Backuper().backup(path);

        Path tempPath = Paths.get(path.getFileName().toString() + ".tmp");
        createTmpFile(tempPath);
        replaceWord(path, tempPath);
        replaceOriginFile(path, tempPath);
    }

    private void replaceOriginFile(Path path, Path tempPath) {
        try {
            Files.delete(path);
            Files.move(tempPath, path);
            Runtime.getRuntime().exec("chmod +x " + path.getFileName().toString());
        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }

    public void replaceWord(Path path, Path tempPath) {
        SearchResult searchResult = makeSearch(path, Constant.PATTERN);
        if (!searchResult.isPatternWasFound())
            throw new RuntimeException("Replacement pattern was not found");

        writeBeforeReplacement(path, searchResult, tempPath);
        writeReplacement(tempPath);
        writeAfterReplacement(path, searchResult, tempPath);
    }

    private void writeAfterReplacement(Path path, SearchResult searchResult, Path tempPath) {
        try (
                FileChannel originChanel = FileChannel.open(path, StandardOpenOption.READ);
                FileChannel tempChanel = FileChannel.open(tempPath, StandardOpenOption.WRITE);
        ) {
            long afterTargetWord = searchResult.getPosition() + Constant.TARGET_WORD.length;
            long count = originChanel.size() - afterTargetWord;
            originChanel.position(afterTargetWord);
            tempChanel.transferFrom(originChanel, afterTargetWord, count);
        } catch (IOException x) {
            log.severe("I/O Exception: " + x);
        }
    }

    private void writeReplacement(Path tempPath) {
        try (
                FileChannel tempChanel = FileChannel.open(tempPath, StandardOpenOption.WRITE);
        ) {
            tempChanel.position(tempChanel.size());
            tempChanel.write(ByteBuffer.wrap(Constant.REPLACEMENT));
        } catch (IOException x) {
            log.severe("I/O Exception: " + x);
        }
    }

    private void writeBeforeReplacement(Path path, SearchResult searchResult, Path tempPath) {
        try (
                FileChannel originChanel = FileChannel.open(path, StandardOpenOption.READ);
                FileChannel tempChanel = FileChannel.open(tempPath, StandardOpenOption.WRITE);
        ) {
            tempChanel.transferFrom(originChanel, 0, searchResult.getPosition());
        } catch (IOException x) {
            log.severe("I/O Exception: " + x);
        }
    }

    private static SearchResult makeSearch(Path path, byte[] pattern) {
        SearchResult searchResult = null;
        try (
                FileChannel originChanel = FileChannel.open(path, StandardOpenOption.READ);
        ) {
            Searcher searcher = new Searcher();
            searchResult = searcher.findPatternPosition(originChanel, pattern);

            if (searchResult.isPatternWasFound()) {
                correctToTargetWord(searchResult, searcher);
            }
        } catch (IOException x) {
            log.severe("I/O Exception: " + x);
        }
        return searchResult;
    }

    private static void correctToTargetWord(SearchResult searchResult, Searcher searcher) {
        long targetWordOffset = searcher.findTargetWordInPattern(Constant.PATTERN, Constant.TARGET_WORD);
        long patternOffset = searchResult.getPosition();
        searchResult.setPosition(patternOffset + targetWordOffset);
    }

    public void createTmpFile(Path tempPath) {
        try {
            Files.deleteIfExists(tempPath);
            Files.createFile(tempPath);
        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }

    public void revertFromBackup(Path path) {
        new Backuper().revert(path);
    }

    public static boolean isFilePatched(Path path) {
        SearchResult searchResult = makeSearch(path, Constant.PATCHED_PATTERN);
        return searchResult.isPatternWasFound();
    }
}
