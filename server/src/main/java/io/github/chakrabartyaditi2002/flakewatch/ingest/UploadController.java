package io.github.chakrabartyaditi2002.flakewatch.ingest;

import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReceivedFile;
import io.github.chakrabartyaditi2002.flakewatch.ingest.internal.ReportFiles;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.GlobalExceptionHandler.FieldError;
import io.github.chakrabartyaditi2002.flakewatch.platform.error.ValidationFailedException;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectAccess;
import io.github.chakrabartyaditi2002.flakewatch.projects.ProjectRef;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenPrincipal;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenScope;
import io.github.chakrabartyaditi2002.flakewatch.projects.TokenService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Comparator;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Upload API (FR-ING-001 to FR-ING-005, FR-ING-008, FR-ING-011). Contract: api/openapi.yaml. */
@RestController
@RequestMapping("/api/v1")
public class UploadController {

    private final TokenService tokens;
    private final UploadService uploads;
    private final UploadQueries queries;
    private final ProjectAccess projects;
    private final Validator validator;
    private final String publicUrl;

    public UploadController(
            TokenService tokens,
            UploadService uploads,
            UploadQueries queries,
            ProjectAccess projects,
            Validator validator,
            @Value("${flakewatch.public-url:http://localhost:8080}") String publicUrl) {
        this.tokens = tokens;
        this.uploads = uploads;
        this.queries = queries;
        this.projects = projects;
        this.validator = validator;
        this.publicUrl = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
    }

    @PostMapping(path = "/uploads", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadAccepted> create(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestPart(value = "metadata", required = false) UploadMetadata metadata,
            @RequestPart(value = "files", required = false) List<MultipartFile> files) {
        TokenPrincipal caller = tokens.authenticate(authorization, TokenScope.INGEST);
        if (metadata == null) {
            throw new ValidationFailedException(List.of(new FieldError("metadata", "is required")));
        }
        validate(metadata);
        List<ReceivedFile> received = ReportFiles.read(files);
        ProjectRef project = projects.byId(caller.projectId());

        UploadService.Accepted accepted = uploads.accept(project.id(), metadata.normalized(), received);
        UploadAccepted body = new UploadAccepted(
                accepted.uploadId(),
                accepted.runId(),
                accepted.status(),
                "/api/v1/uploads/" + accepted.uploadId(),
                publicUrl + "/p/" + project.slug() + "/runs/" + accepted.runId(),
                accepted.duplicate());
        return ResponseEntity.status(accepted.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED).body(body);
    }

    @GetMapping("/uploads/{uploadId}")
    public UploadStatus status(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @PathVariable long uploadId) {
        TokenPrincipal caller = tokens.authenticate(authorization, TokenScope.INGEST);
        return queries.status(caller.projectId(), uploadId).orElseThrow(ProjectAccess::notFound);
    }

    private void validate(UploadMetadata metadata) {
        List<FieldError> errors = validator.validate(metadata).stream()
                .sorted(Comparator.comparing((ConstraintViolation<UploadMetadata> v) -> v.getPropertyPath().toString()))
                .map(v -> new FieldError("metadata." + v.getPropertyPath(), v.getMessage()))
                .toList();
        if (!errors.isEmpty()) {
            throw new ValidationFailedException(errors);
        }
        if (metadata.repositoryUrl() != null && !metadata.repositoryUrl().isBlank()
                && !metadata.repositoryUrl().matches("^https?://\\S+$")) {
            throw new ValidationFailedException(List.of(new FieldError("metadata.repositoryUrl", "must be an http(s) URL")));
        }
    }
}
