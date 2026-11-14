package com.requestmanagement.backend.guesttracking;

/** Test-only access to the session token digest, to seed a verified Guest session without OTP email. */
public final class GuestTrackingTestSupport {
    private GuestTrackingTestSupport() { }

    public static String sessionDigest(GuestTrackingCrypto crypto, String token) {
        return crypto.digest("session", token);
    }
}
