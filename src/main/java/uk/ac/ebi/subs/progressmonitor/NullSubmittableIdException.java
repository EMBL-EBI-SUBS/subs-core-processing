package uk.ac.ebi.subs.progressmonitor;

/**
 * Specific {@link Exception} to throw when the submission Id
 * of a {@link uk.ac.ebi.subs.processing.ProcessingCertificate} is NULL.
 */
public class NullSubmittableIdException extends RuntimeException {

    public NullSubmittableIdException(String message) {
        super(message);
    }
}
