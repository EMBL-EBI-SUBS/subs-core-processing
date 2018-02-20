package uk.ac.ebi.subs.batchloader;

class CouldNotGetTokenException extends RuntimeException {
    public CouldNotGetTokenException(String message) {
        super(message);
    }
}
