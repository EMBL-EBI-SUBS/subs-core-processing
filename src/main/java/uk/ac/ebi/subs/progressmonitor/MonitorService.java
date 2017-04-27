package uk.ac.ebi.subs.progressmonitor;

import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;

/**
 * Created by davidr on 25/04/2017.
 */
public interface MonitorService {

    /**
     * store supporting information received from archives
     * @param submissionEnvelope
     */
    void storeSupportingInformation(SubmissionEnvelope submissionEnvelope);

    /**
     * update accessions + statuses using information in a processingCertificateEnvelop
     * @param processingCertificateEnvelope
     */
    void updateSubmittablesFromCertificates(ProcessingCertificateEnvelope processingCertificateEnvelope);
}
