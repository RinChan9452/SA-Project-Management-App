package com.projectsa.document;

import com.projectsa.common.FieldValidationException;
import com.projectsa.common.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Saves uploads under {@code app.upload-dir} with generated names and reads them back.
 * Stored paths are relative to the upload root and can never point outside it.
 */
@Service
public class FileStorage {

    public static final long MAX_DOCUMENT_BYTES = 50L * 1024 * 1024;

    private final Path root;

    public FileStorage(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    /**
     * Checks size and real content type of an upload.
     *
     * @throws FieldValidationException on {@code field} if empty, too large, or not one of {@code allowed}
     */
    public FileKind check(MultipartFile file, Set<FileKind> allowed, long maxBytes, String field) {
        if (file == null || file.isEmpty()) {
            throw new FieldValidationException(field, "Please choose a file");
        }
        if (file.getSize() > maxBytes) {
            throw new FieldValidationException(field, "The file is too large (max " + maxBytes / (1024 * 1024) + " MB)");
        }
        byte[] head = readHead(file);
        return allowed.stream()
                .filter(kind -> kind.matches(head))
                .findFirst()
                .orElseThrow(() -> new FieldValidationException(field, "Only " + names(allowed) + " files are allowed"));
    }

    /** Saves the file as {@code <folder>/<uuid><ext>} and returns that relative path. */
    public String save(MultipartFile file, FileKind kind, String folder) {
        String relative = folder + "/" + UUID.randomUUID() + kind.getExtension();
        Path target = resolve(relative);
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save the uploaded file", e);
        }
        return relative;
    }

    public Resource load(String relativePath) {
        Path path = resolve(relativePath);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("File not found");
        }
        return new FileSystemResource(path);
    }

    /** Best effort; used to clean up when saving the database record fails. */
    public void deleteQuietly(String relativePath) {
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | RuntimeException ignored) {
            // nothing else to do
        }
    }

    /** The name to show and offer on download: only the file name part, never a client path. */
    public static String originalName(MultipartFile file, FileKind kind) {
        String fallback = "document" + (kind == null ? "" : kind.getExtension());
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank()) {
            return fallback;
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.length() > 255) {
            name = name.substring(name.length() - 255);
        }
        return name.isEmpty() ? fallback : name;
    }

    Path resolve(String relativePath) {
        Path path = root.resolve(relativePath).normalize();
        if (!path.startsWith(root)) {
            throw new NotFoundException("File not found");
        }
        return path;
    }

    private static byte[] readHead(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(FileKind.longestSignature());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    private static String names(Set<FileKind> kinds) {
        return Arrays.stream(FileKind.values()).filter(kinds::contains).map(Enum::name)
                .collect(Collectors.joining(", "));
    }
}
