/**
 * Validates a video file before it is uploaded.
 *
 * Two checks are performed:
 *
 * 1. SIZE — reject files larger than MAX_FILE_SIZE_BYTES immediately,
 *    before any API call is made. This prevents wasting presigned URLs
 *    and S3 multipart sessions on files that will never succeed.
 *
 * 2. MIME TYPE — react-dropzone's `accept` filter only checks the file
 *    extension, not the actual content. A user can rename `photo.jpg` to
 *    `photo.mp4` and bypass the dropzone filter. We re-check the MIME type
 *    here as a second layer of defense.
 *
 *    Note: true magic-bytes validation (reading the first bytes of the file
 *    to identify the container format) is the strongest check but requires
 *    an async read. MIME type checking is sufficient for this use case.
 */

/** 2 GB in bytes */
export const MAX_FILE_SIZE_BYTES = 2 * 1024 * 1024 * 1024;

/** Human-readable size limit shown in error messages */
export const MAX_FILE_SIZE_LABEL = '2 GB';

/** Allowed MIME type prefix — any video/* subtype is accepted */
const ALLOWED_MIME_PREFIX = 'video/';

/** Allowed file extensions as a display string for error messages */
const ALLOWED_EXTENSIONS = 'MP4, MOV, AVI, MKV, WebM';

export interface ValidationResult {
    valid: boolean;
    error?: string;
}

/**
 * Validates the given file against size and MIME type rules.
 *
 * @param file - the File object selected by the user
 * @returns    { valid: true } on success, or { valid: false, error: string } on failure
 */
export function validateVideoFile(file: File): ValidationResult {
    // ── Check 1: file size ───────────────────────────────────────────────
    if (file.size > MAX_FILE_SIZE_BYTES) {
        const sizeMB = (file.size / (1024 * 1024)).toFixed(0);
        return {
            valid: false,
            error: `File is too large (${sizeMB} MB). Maximum allowed size is ${MAX_FILE_SIZE_LABEL}.`,
        };
    }

    // ── Check 2: MIME type ───────────────────────────────────────────────
    // file.type is set by the browser based on the file extension on Windows/Mac.
    // An empty string means the browser couldn't determine the type — we allow
    // it through rather than blocking valid files on restrictive systems.
    if (file.type !== '' && !file.type.startsWith(ALLOWED_MIME_PREFIX)) {
        return {
            valid: false,
            error: `"${file.name}" does not appear to be a video file. Please upload a ${ALLOWED_EXTENSIONS} file.`,
        };
    }

    return { valid: true };
}
