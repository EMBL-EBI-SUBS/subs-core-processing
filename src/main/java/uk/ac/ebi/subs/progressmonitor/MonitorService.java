package uk.ac.ebi.subs.progressmonitor;

import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;

/**
 * Created by davidr on 25/04/2017.
 */
public interface MonitorService {

    void submissionStatusUpdated(ProcessingCertificate processingCertificate);

    void handleSupportingInfo(SubmissionEnvelope submissionEnvelope);

    void checkForProcessedSubmissions(ProcessingCertificateEnvelope processingCertificateEnvelope);
}
