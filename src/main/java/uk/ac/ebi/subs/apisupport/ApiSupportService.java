package uk.ac.ebi.subs.apisupport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;
import uk.ac.ebi.subs.validator.repository.ValidationResultRepository;

import java.util.List;

/**
 * This is a Spring @Service component for {@link Submission} entity.
 */
@Service
public class ApiSupportService {

    private static final Logger logger = LoggerFactory.getLogger(ApiSupportService.class);

    @Autowired private List<SubmittableRepository<?>> submissionContentsRepositories;
    @Autowired private ProcessingStatusRepository processingStatusRepository;
    @Autowired private SubmissionStatusRepository submissionStatusRepository;
    @Autowired private SubmissionRepository submissionRepository;
    @Autowired private ValidationResultRepository validationResultRepository;

    /**
     * After a submission has been deleted through the API, cleanup its lingering contents
     *
     * @param submission
     */
    public void deleteSubmissionContents(Submission submission) {

        logger.info("deleting submission {}", submission);

        processingStatusRepository.deleteBySubmissionId(submission.getId());
        logger.debug("deleted processing statuses for submission {}",submission);

        submissionStatusRepository.delete(submission.getSubmissionStatus());
        logger.debug("deleted submission status for submission {}",submission);

        submissionContentsRepositories.stream().forEach(repo -> repo.deleteBySubmissionId(submission.getId()));
        logger.debug("deleted contents of submission {}",submission);

        validationResultRepository.deleteAllBySubmissionId(submission.getId());
        logger.debug("deleted submission {} validation results", submission.getId());
    }

    /**
     * Once a submission has been submitted, change the processing status of its submittables from 'draft' to 'submitted'
     *
     * @param submission
     */
    public void markContentsAsSubmitted(Submission submission) {

        Submission currentSubmissionState = submissionRepository.findOne(submission.getId());
        if (SubmissionStatusEnum.Draft.name().equals(currentSubmissionState.getSubmissionStatus().getStatus())) {
            logger.info("not safe to set submission contents to submitted, still in draft in db {}",submission);
            return; //status update did not succeed, return
        }

        logger.info("setting submission contents to submitted {}",submission);

        submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.streamBySubmissionId(submission.getId()))
                .filter(item -> ProcessingStatusEnum.Draft.name().equals(item.getProcessingStatus().getStatus()))
                .map(item -> {
                    ProcessingStatus status = item.getProcessingStatus();
                    status.copyDetailsFromSubmittable(item);
                    status.setStatus(ProcessingStatusEnum.Submitted);
                    return status;
                })
                .forEach(processingStatus -> processingStatusRepository.save(processingStatus))
        ;

        logger.debug("set submission contents to submitted {}",submission);

    }
}
