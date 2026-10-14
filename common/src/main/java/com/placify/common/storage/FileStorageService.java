package com.placify.common.storage;

import com.placify.common.web.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * Stores uploaded files (resumes, photos, logos, offer letters, snapshots) on
 * local disk under {@code placify.storage.root}. Stored names are random UUIDs
 * and every read is checked so that no path can escape the storage folder.
 */
@Slf4j
@Component
public class FileStorageService {

    private final Path root;

    public FileStorageService(@Value("${placify.storage.root:./storage}") String root) {
        this.root = Paths.get(root).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot create storage folder " + this.root, ex);
        }
    }

    /**
     * Saves the bytes in the given folder and returns the relative path
     * (folder/uuid.ext) to keep in the database.
     */
    public String save(String folder, byte[] content, String extension) {
        String safeFolder = folder.replaceAll("[^a-zA-Z0-9_-]", "");
        String safeExt = extension == null ? "" : extension.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String name = UUID.randomUUID() + (safeExt.isEmpty() ? "" : "." + safeExt);
        Path dir = root.resolve(safeFolder);
        try {
            Files.createDirectories(dir);
            Files.write(dir.resolve(name), content);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store file", ex);
        }
        return safeFolder + "/" + name;
    }

    public byte[] read(String relativePath) {
        Path file = resolve(relativePath);
        if (!Files.exists(file)) {
            throw ApiException.notFound("File not found");
        }
        try {
            return Files.readAllBytes(file);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read file", ex);
        }
    }

    public boolean exists(String relativePath) {
        return relativePath != null && Files.exists(resolve(relativePath));
    }

    public void delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException ex) {
            log.warn("Could not delete {}: {}", relativePath, ex.getMessage());
        }
    }

    private Path resolve(String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw ApiException.forbidden("Invalid file path");
        }
        return file;
    }

    public static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1);
    }
}
