package com.projectsa.document;

import com.projectsa.auth.CurrentUser;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import java.time.Duration;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@Controller
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** Download by document ID; the file path always comes from the database, never from the URL. */
    @GetMapping("/documents/{id}")
    ResponseEntity<Resource> download(@PathVariable Long id, @AuthenticationPrincipal CurrentUser user) {
        DocumentService.Download d = documentService.forDownload(id, user);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(d.document().getOriginalName(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(MediaType.parseMediaType(d.document().getMime()))
                .contentLength(d.document().getSizeBytes())
                .body(d.resource());
    }

    /**
     * F4 profile picture. Pages add {@code ?v=<file name>} to the URL, which changes with every new picture,
     * so the browser may keep it for a day.
     */
    @GetMapping("/employees/{id}/avatar")
    ResponseEntity<Resource> avatar(@PathVariable Long id) {
        DocumentService.Avatar a = documentService.avatar(id);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .contentType(MediaType.parseMediaType(a.kind().getMime()))
                .body(a.resource());
    }
}
