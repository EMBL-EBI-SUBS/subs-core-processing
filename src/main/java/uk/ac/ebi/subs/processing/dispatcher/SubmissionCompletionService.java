package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;

import java.util.Map;

/**
 * This is a Spring @Service component for {@link Submission} entity, that is dealing with {@link Submission} completion.
 */
@Service
public class SubmissionCompletionService {

    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private SubmissionRepository submissionRepository;

    public SubmissionCompletionService(ProcessingStatusRepository processingStatusRepository,
                                       SubmissionStatusRepository submissionStatusRepository,
                                       SubmissionRepository submissionRepository) {
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
        this.submissionRepository = submissionRepository;
    }

    public boolean allSubmittablesCompleted(String submissionId) {
        Map<String,Integer> statusSummary = processingStatusRepository.summariseSubmissionStatus(submissionId);

        //all submittables are complete if the summary of processing statuses has one status, and that status is 'Completed'
        return (statusSummary.size() == 1 && statusSummary.containsKey(ProcessingStatusEnum.Completed.name()));
    }

    public void markSubmissionAsCompleted(String submissionId){
        Submission submission = submissionRepository.findOne(submissionId);
        SubmissionStatus submissionStatus = submission.getSubmissionStatus();
        submissionStatus.setStatus(SubmissionStatusEnum.Completed);
        submissionStatusRepository.save(submissionStatus);
    }
}
