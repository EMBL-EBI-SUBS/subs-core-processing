package uk.ac.ebi.subs.apisupport;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.error.EntityNotFoundException;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;
import uk.ac.ebi.subs.validator.repository.ValidationResultRepository;

import java.util.List;
import java.util.Optional;

/**
 * This is a Spring @Service component for dealing with works after deleted a {@link Submission} entity
 * or marks submission contents as Submitted.
 */
@Service
public class ApiSupportService {

    private static final Logger logger = LoggerFactory.getLogger(ApiSupportService.class);

    private final List<SubmittableRepository<?>> submissionContentsRepositories;
    private final ProcessingStatusRepository processingStatusRepository;
    private final SubmissionStatusRepository submissionStatusRepository;
    private final SubmissionRepository submissionRepository;
    private final ValidationResultRepository validationResultRepository;

    public ApiSupportService(List<SubmittableRepository<?>> submissionContentsRepositories,
                             ProcessingStatusRepository processingStatusRepository,
                             SubmissionStatusRepository submissionStatusRepository,
                             SubmissionRepository submissionRepository,
                             ValidationResultRepository validationResultRepository) {
        this.submissionContentsRepositories = submissionContentsRepositories;
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
        this.submissionRepository = submissionRepository;
        this.validationResultRepository = validationResultRepository;
    }

    /**
     * After a submission has been deleted through the API, cleanup its lingering contents
     *
     * @param submission the {@link Submission} entity that has been deleted
     */
    public void deleteSubmissionContents(Submission submission) {
        logger.info("deleting submission {}", submission);

        processingStatusRepository.deleteBySubmissionId(submission.getId());
        logger.debug("deleted processing statuses for submission {}",submission);

        submissionStatusRepository.delete(submission.getSubmissionStatus());
        logger.debug("deleted submission status for submission {}",submission);

        submissionContentsRepositories.forEach(repo -> repo.deleteBySubmissionId(submission.getId()));
        logger.debug("deleted contents of submission {}",submission);

        validationResultRepository.deleteAllBySubmissionId(submission.getId());
        logger.debug("deleted submission {} validation results", submission.getId());
    }

    /**
     * Once a submission has been submitted, change the processing status of its submittables from 'draft' to 'submitted'
     *
     * @param submission the {@link Submission} entity that has been submitted
     */
    public void markContentsAsSubmitted(uk.ac.ebi.subs.data.Submission submission) {

        final String submissionId = submission.getId();
        Submission currentSubmission = Optional.of(submissionRepository.findOne(submissionId))
            .orElseThrow(() -> new EntityNotFoundException(
                String.format("Submission entity with ID: %s is not found in the database.", submissionId)));

        if (SubmissionStatusEnum.Draft.name().equals(currentSubmission.getSubmissionStatus().getStatus())) {
            logger.info("not safe to set submission contents to submitted, still in draft in db {}",submission);
            return; //status update did not succeed, return
        }

        logger.info("setting submission contents to submitted {}",submission);

        submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.streamBySubmissionId(submissionId))
                .filter(item -> ProcessingStatusEnum.Draft.name().equals(item.getProcessingStatus().getStatus()))
                .map(item -> {
                    ProcessingStatus status = item.getProcessingStatus();
                    status.copyDetailsFromSubmittable(item);
                    status.setStatus(ProcessingStatusEnum.Submitted);
                    return status;
                })
                .forEach(processingStatusRepository::save);

        logger.debug("set submission contents to submitted {}",submission);
    }
}
