import axios from "axios";

/**
 * Shared Axios instance pre-configured with the backend base URL.
 *
 * Uses NEXT_PUBLIC_API_BASE_URL to match the env var defined in .env.local.
 * All backend API calls should use this instance so the base URL and
 * default headers are applied consistently.
 *
 * Note: Direct S3 upload calls (PUT to presigned URLs) must use plain axios,
 * NOT this instance — they go to S3, not the backend, and must not carry
 * the ngrok or other backend-specific headers.
 */
export const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_BASE_URL || "http://localhost:8080/videos",
    headers: {
        // Prevents ngrok from showing the browser warning page in dev tunnels
        "ngrok-skip-browser-warning": "true",
    },
});
