package com.projectsa.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.projectsa.common.FieldValidationException;
import com.projectsa.common.NotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class FileStorageTest {

    @TempDir
    Path tmp;

    static final byte[] PDF = "%PDF-1.4\nhello".getBytes(StandardCharsets.US_ASCII);
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1};

    FileStorage storage() {
        return new FileStorage(tmp.toString());
    }

    @Test
    void recognisesFilesByContent() {
        FileStorage s = storage();
        Set<FileKind> all = Set.of(FileKind.values());
        assertThat(s.check(new MockMultipartFile("f", "a.pdf", null, PDF), all, 100, "f")).isEqualTo(FileKind.PDF);
        assertThat(s.check(new MockMultipartFile("f", "a.png", null, PNG), all, 100, "f")).isEqualTo(FileKind.PNG);
        assertThat(s.check(new MockMultipartFile("f", "a.jpg", null, JPG), all, 100, "f")).isEqualTo(FileKind.JPG);
    }

    @Test
    void fakePdfWithPdfNameIsRejected() {
        MockMultipartFile fake = new MockMultipartFile("f", "virus.pdf", "application/pdf", "MZ not a pdf".getBytes());
        assertThatThrownBy(() -> storage().check(fake, Set.of(FileKind.PDF), 100, "f"))
                .isInstanceOf(FieldValidationException.class)
                .hasMessageContaining("Only PDF");
    }

    @Test
    void emptyAndTooLargeAreRejected() {
        assertThatThrownBy(() -> storage().check(new MockMultipartFile("f", new byte[0]), Set.of(FileKind.PDF), 100, "f"))
                .hasMessageContaining("choose a file");
        assertThatThrownBy(() -> storage().check(new MockMultipartFile("f", "a.pdf", null, PDF), Set.of(FileKind.PDF), 5, "f"))
                .hasMessageContaining("too large");
    }

    @Test
    void savesWithGeneratedNameAndLoadsBack() throws Exception {
        FileStorage s = storage();
        String path = s.save(new MockMultipartFile("f", "../../evil.pdf", null, PDF), FileKind.PDF, "projects/7");
        assertThat(path).startsWith("projects/7/").endsWith(".pdf").doesNotContain("evil");
        assertThat(Files.readAllBytes(s.load(path).getFile().toPath())).isEqualTo(PDF);
    }

    @Test
    void pathsOutsideTheUploadFolderAreRefused() {
        assertThatThrownBy(() -> storage().load("../outside.txt")).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> storage().load("projects/../../outside.txt")).isInstanceOf(NotFoundException.class);
    }
}
