package uk.ac.ebi.subs.progressmonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.processing.ProcessingCertificate;
import uk.ac.ebi.subs.processing.ProcessingCertificateEnvelope;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.processing.SupportingSample;
import uk.ac.ebi.subs.repository.processing.SupportingSampleRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProgressMonitorService {
    private static final Logger logger = LoggerFactory.getLogger(ProgressMonitorService.class);

    private SupportingSampleRepository supportingSampleRepository;
    private ProcessingStatusRepository processingStatusRepository;
    private SubmittablesBulkOperations submittablesBulkOperations;

    public ProgressMonitorService(SupportingSampleRepository supportingSampleRepository, ProcessingStatusRepository processingStatusRepository, SubmittablesBulkOperations submittablesBulkOperations) {
        this.supportingSampleRepository = supportingSampleRepository;
        this.processingStatusRepository = processingStatusRepository;
        this.submittablesBulkOperations = submittablesBulkOperations;
    }

    /**
     * store supporting information received from archives
     * @param submissionEnvelope the container contains the supporting samples information
     */
    public void storeSupportingInformation(SubmissionEnvelope submissionEnvelope) {

        final String submissionId = submissionEnvelope.getSubmission().getId();


        List<SupportingSample> supportingSamples = submissionEnvelope.getSupportingSamples().stream()
                .map(s -> new SupportingSample(submissionId, s))
                .collect(Collectors.toList());

        //store supporting info,
        logger.info(
                "storing supporting sample info for submission {}, {} samples",
                submissionEnvelope.getSubmission().getId(),
                supportingSamples.size()
        );
        supportingSampleRepository.save(supportingSamples);
    }

    /**
     * update accessions + statuses using information in a processingCertificateEnvelop
     * @param processingCertificateEnvelope container contains the processing related information of a given submission
     */
    public void updateSubmittablesFromCertificates(ProcessingCertificateEnvelope processingCertificateEnvelope) {

        logger.info("received agent results for submission {} with {} certificates ",
                processingCertificateEnvelope.getSubmissionId(), processingCertificateEnvelope.getProcessingCertificates().size());


        for (ProcessingCertificate cert : processingCertificateEnvelope.getProcessingCertificates()) {

            if (cert.getSubmittableId() == null) {
                throw new NullSubmittableIdException("Processing Certificate Submittable Id can't be NULL.");
            }

            ProcessingStatus processingStatus = processingStatusRepository.findBySubmittableId(cert.getSubmittableId());

            if (processingStatus == null) {
                continue;
            }

            if (cert.getAccession() != null) {
                processingStatus.setAccession(cert.getAccession());
            }

            processingStatus.setArchive(cert.getArchive().name());
            processingStatus.setMessage(cert.getMessage());

            processingStatus.setStatus(cert.getProcessingStatus());

            processingStatus.setLastModifiedBy(cert.getArchive().name());
            processingStatus.setLastModifiedDate(new Date());

            processingStatusRepository.save(processingStatus);
        }

        submittablesBulkOperations.applyProcessingCertificates(processingCertificateEnvelope);
    }
}
