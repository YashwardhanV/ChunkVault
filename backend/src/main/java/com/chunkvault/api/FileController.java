package com.chunkvault.api;

import com.chunkvault.domain.ChunkingStrategy;
import com.chunkvault.dto.FileDetailResponse;
import com.chunkvault.dto.FileSummaryResponse;
import com.chunkvault.dto.PageResponse;
import com.chunkvault.dto.UploadResponse;
import com.chunkvault.dto.VersionComparisonResponse;
import com.chunkvault.dto.VersionResponse;
import com.chunkvault.service.FileDownloadService;
import com.chunkvault.service.FileQueryService;
import com.chunkvault.service.UploadService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/files")
public class FileController {
    private final UploadService uploads;
    private final FileQueryService queries;
    private final FileDownloadService downloads;

    public FileController(UploadService uploads, FileQueryService queries, FileDownloadService downloads) {
        this.uploads = uploads;
        this.queries = queries;
        this.downloads = downloads;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "FIXED_SIZE") ChunkingStrategy strategy) throws IOException {
        try (InputStream input = file.getInputStream()) {
            UploadResponse response = uploads.uploadNew(file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), strategy, input);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }

    @PostMapping(path = "/{id}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> uploadVersion(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "FIXED_SIZE") ChunkingStrategy strategy) throws IOException {
        try (InputStream input = file.getInputStream()) {
            UploadResponse response = uploads.uploadVersion(id, file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), strategy, input);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        }
    }

    @GetMapping
    public PageResponse<FileSummaryResponse> list(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return queries.list(page, size);
    }

    @GetMapping("/{id}")
    public FileDetailResponse detail(@PathVariable UUID id,
                                     @RequestParam(required = false) Integer version) {
        return queries.detail(id, version);
    }

    @GetMapping("/{id}/versions")
    public List<VersionResponse> versions(@PathVariable UUID id) {
        return queries.versions(id);
    }

    @GetMapping("/{id}/versions/compare")
    public VersionComparisonResponse compare(@PathVariable UUID id, @RequestParam @Min(1) int from,
                                             @RequestParam @Min(1) int to) {
        return queries.compare(id, from, to);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable UUID id) {
        return downloadResponse(downloads.prepare(id, null));
    }

    @GetMapping("/{id}/versions/{version}/download")
    public ResponseEntity<StreamingResponseBody> downloadVersion(@PathVariable UUID id,
                                                                 @PathVariable @Min(1) int version) {
        return downloadResponse(downloads.prepare(id, version));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        queries.delete(id);
        return ResponseEntity.noContent().build();
    }

    private ResponseEntity<StreamingResponseBody> downloadResponse(FileDownloadService.DownloadDescriptor descriptor) {
        StreamingResponseBody body = output -> downloads.stream(descriptor, output);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(descriptor.contentType()));
        headers.setContentLength(descriptor.size());
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(descriptor.filename(), StandardCharsets.UTF_8).build());
        headers.set("X-Content-SHA256", descriptor.fileHash());
        return new ResponseEntity<>(body, headers, HttpStatus.OK);
    }
}
