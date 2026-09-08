import axios from 'axios';

/**
 * Shared axios instance for all backend API calls.
 *
 * Base URL is read from NEXT_PUBLIC_API_BASE_URL so it works across
 * local dev (localhost), staging (ngrok), and production without code changes.
 *
 * ⚠️  Do NOT use this instance for direct S3 PUT calls (presigned URLs).
 *     Those go to S3, not the backend, and must use plain axios so they don't
 *     carry backend-specific headers that would break the S3 signature check.
 */
export const api = axios.create({
    baseURL: process.env.NEXT_PUBLIC_API_BASE_URL ?? 'http://localhost:8080/videos',
    headers: {
        // Bypasses the ngrok browser-warning interstitial page, which otherwise
        // strips CORS headers before they reach the client.
        'ngrok-skip-browser-warning': 'true',
    },
});
