package uk.ac.ebi.subs.apisupport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.List;

@Service
public class ApiSupportServiceImpl implements ApiSupportService {

    private static final Logger logger = LoggerFactory.getLogger(ApiSupportServiceImpl.class);

    private List<SubmittableRepository<?>> submissionContentsRepositories;
    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private SubmissionRepository submissionRepository;

    public ApiSupportServiceImpl(List<SubmittableRepository<?>> submissionContentsRepositories, ProcessingStatusRepository processingStatusRepository, SubmissionStatusRepository submissionStatusRepository, SubmissionRepository submissionRepository) {
        this.submissionContentsRepositories = submissionContentsRepositories;
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    public void deleteSubmissionContents(Submission submission) {

        if (submissionRepository.findOne(submission.getId()) != null) {
            logger.info("not safe to delete submission, still in db {}",submission);
            return; // submission was not actually deleted
        }

        logger.info("deleting submission {}",submission);

        processingStatusRepository.deleteBySubmissionId(submission.getId());

        logger.debug("deleted processing statuses for submission {}",submission);


        submissionStatusRepository.delete(submission.getSubmissionStatus());

        logger.debug("deleted submission status for submission {}",submission);

        submissionContentsRepositories.stream().forEach(repo -> repo.deleteBySubmissionId(submission.getId()));

        logger.debug("deleted contents of submission {}",submission);

    }

    @Override
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
