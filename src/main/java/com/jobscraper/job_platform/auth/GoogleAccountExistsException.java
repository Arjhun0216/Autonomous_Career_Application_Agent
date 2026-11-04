package com.jobscraper.job_platform.auth;

public class GoogleAccountExistsException
        extends RuntimeException {

    public GoogleAccountExistsException(String message) {
        super(message);
    }
}