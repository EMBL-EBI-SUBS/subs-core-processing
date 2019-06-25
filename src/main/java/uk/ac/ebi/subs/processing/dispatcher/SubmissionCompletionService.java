package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * This is a Spring @Service component for {@link Submission} entity, that is dealing with {@link Submission} completion.
 */
@Service
public class SubmissionCompletionService {

    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private SubmissionRepository submissionRepository;

    private final static List<String> FINISHED_PROCESSINGSTATUSES =
            Arrays.asList(ProcessingStatusEnum.Completed.name(), ProcessingStatusEnum.ArchiveDisabled.name());

    public SubmissionCompletionService(ProcessingStatusRepository processingStatusRepository,
                                       SubmissionStatusRepository submissionStatusRepository,
                                       SubmissionRepository submissionRepository) {
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
        this.submissionRepository = submissionRepository;
    }

    public boolean allSubmittablesCompleted(String submissionId) {
        Map<String,Integer> statusSummary = processingStatusRepository.summariseSubmissionStatus(submissionId);

        for(String statusKey : statusSummary.keySet()) {
            if (!FINISHED_PROCESSINGSTATUSES.contains(statusKey)) {
                return false;
            }
        }

        return true;
    }

    public void markSubmissionAsCompleted(String submissionId){
        Submission submission = submissionRepository.findOne(submissionId);
        SubmissionStatus submissionStatus = submission.getSubmissionStatus();
        submissionStatus.setStatus(SubmissionStatusEnum.Completed);
        submissionStatusRepository.save(submissionStatus);
    }
}
