package nl.metafactory.aicontrol.specqueue.app;

import org.springframework.http.HttpStatus;

public class SpecQueueException extends RuntimeException {

    public enum Code {
        VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
        AUTO_MERGE_NOT_ALLOWED(HttpStatus.BAD_REQUEST),
        WORKFLOW_PROMPT_REQUIRED(HttpStatus.BAD_REQUEST),
        WORKFLOW_NOT_ALLOWED_FOR_PROJECT(HttpStatus.BAD_REQUEST),

        PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND),
        SPEC_FILE_NOT_FOUND(HttpStatus.NOT_FOUND),
        WORKFLOW_NOT_FOUND(HttpStatus.NOT_FOUND),
        ITEM_NOT_FOUND(HttpStatus.NOT_FOUND),

        DUPLICATE_SPEC_FILE(HttpStatus.CONFLICT),
        PROJECT_INACTIVE(HttpStatus.CONFLICT),
        GIT_URL_MISSING(HttpStatus.CONFLICT),
        GIT_AUTH_FAILED(HttpStatus.CONFLICT),
        QUEUE_FULL(HttpStatus.CONFLICT),
        QUEUE_BUSY(HttpStatus.CONFLICT),
        ITEM_NOT_EDITABLE(HttpStatus.CONFLICT),
        ITEM_NOT_REMOVABLE(HttpStatus.CONFLICT),
        ITEM_BUSY(HttpStatus.CONFLICT),
        STALE_ORDER(HttpStatus.CONFLICT),
        QUEUE_HALTED(HttpStatus.CONFLICT),
        UNRESOLVED_FAILED_ITEM(HttpStatus.CONFLICT),
        ITEM_NOT_FAILED(HttpStatus.CONFLICT),
        SPEC_QUEUE_ITEM_ACTIVE(HttpStatus.CONFLICT),

        SPEC_LISTING_FAILED(HttpStatus.BAD_GATEWAY),
        EMBABEL_UNAVAILABLE(HttpStatus.BAD_GATEWAY);

        private final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }

        public HttpStatus httpStatus() {
            return status;
        }
    }

    private final Code code;

    public SpecQueueException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }

    public HttpStatus getHttpStatus() {
        return code.httpStatus();
    }
}
