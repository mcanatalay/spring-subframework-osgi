package org.springframework.sso;

public class SSORunningException extends Exception {
    private static final long serialVersionUID = 1L;

    public SSORunningException(Exception e){
        super(e);
    }
}
