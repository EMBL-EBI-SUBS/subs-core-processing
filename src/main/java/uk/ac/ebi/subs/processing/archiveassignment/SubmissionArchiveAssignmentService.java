package uk.ac.ebi.subs.processing.archiveassignment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.dispatcher.SubmissionEnvelopeService;
import uk.ac.ebi.subs.repository.model.DataType;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;

@Service
public class SubmissionArchiveAssignmentService {

    private static final Logger logger = LoggerFactory.getLogger(SubmissionArchiveAssignmentService.class);


    private SubmissionEnvelopeService submissionEnvelopeService;
    private ProcessingStatusRepository processingStatusRepository;

    public SubmissionArchiveAssignmentService(SubmissionEnvelopeService submissionEnvelopeService, ProcessingStatusRepository processingStatusRepository) {
        this.submissionEnvelopeService = submissionEnvelopeService;
        this.processingStatusRepository = processingStatusRepository;
    }

    public void assignArchives(Submission submission) {
        logger.info("assigning archives for submission {}", submission);

        submissionEnvelopeService.submissionContents(submission.getId())
                .forEach(storedSubmittable -> {
                    DataType dataType = ((StoredSubmittable) storedSubmittable).getDataType();
                    Archive archive = dataType.getArchive();

                    ProcessingStatus processingStatus = storedSubmittable.getProcessingStatus();
                    processingStatus.setArchive(archive.name());
                    processingStatusRepository.save(processingStatus);
                });
    }

}
