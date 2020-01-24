package uk.ac.ebi.subs.processing.initialsubmissionprocessing;

import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.error.EntityNotFoundException;
import uk.ac.ebi.subs.processing.dispatcher.SubmissionEnvelopeService;
import uk.ac.ebi.subs.repository.model.DataType;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;

import java.util.Optional;

import static uk.ac.ebi.subs.processing.initialsubmissionprocessing.SubmissionStatusMessages.PROCESSING_STARTED_MESSAGE;

/**
 * This is a Spring @Service component for the {@link Submission} entity to assign the {@link ProcessingStatus} for each
 * submittable item to the submittable's archive.
 */
@Service
@AllArgsConstructor
public class SubmissionProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionProcessingService.class);

    private SubmissionEnvelopeService submissionEnvelopeService;
    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private SubmissionRepository submissionRepository;

    public void assignArchives(String submissionId) {
        logger.info("assigning archives for submission {}", submissionId);

        submissionEnvelopeService.submissionContents(submissionId)
                .forEach(storedSubmittable -> {
                    DataType dataType = storedSubmittable.getDataType();
                    Archive archive = dataType.getArchive();

                    ProcessingStatus processingStatus = storedSubmittable.getProcessingStatus();
                    processingStatus.setArchive(archive.name());
                    processingStatusRepository.save(processingStatus);
                });
    }

    public void setSubmissionStatusToProcessing(String submissionId) {
        Submission submission = Optional.of(submissionRepository.findOne(submissionId))
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Submission entity with ID: %s is not found in the database.", submissionId)));

        SubmissionStatus submissionStatus = submission.getSubmissionStatus();
        submissionStatus.setStatus(SubmissionStatusEnum.Processing);
        submissionStatus.setMessage(PROCESSING_STARTED_MESSAGE);

        submissionStatusRepository.save(submissionStatus);
    }
}
