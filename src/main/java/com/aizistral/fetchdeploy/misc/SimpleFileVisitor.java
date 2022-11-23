package com.aizistral.fetchdeploy.misc;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.function.Consumer;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SimpleFileVisitor implements FileVisitor<Path> {
    private final Handler<Path, IOException> visitFile;
    private final Handler<Path, IOException> postVisitDirectory;

    @Override
    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        this.visitFile.accept(file);
        return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
        throw new IOException("Could not read " + file);
    }

    @Override
    public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        this.postVisitDirectory.accept(dir);
        return FileVisitResult.CONTINUE;
    }

    public static SimpleFileVisitor create(Handler<Path, IOException> visitFile) {
        return create(visitFile, dir -> {});
    }

    public static SimpleFileVisitor create(Handler<Path, IOException> visitFile, Handler<Path, IOException> postVisitDirectory) {
        return new SimpleFileVisitor(visitFile, postVisitDirectory);
    }


}
