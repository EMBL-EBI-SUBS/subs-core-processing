package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;

import java.util.Map;

@Component
public class SubmissionCompletionService {

    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;

    public SubmissionCompletionService(ProcessingStatusRepository processingStatusRepository, SubmissionStatusRepository submissionStatusRepository) {
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
    }

    public boolean allSubmittablesCompleted(Submission submission) {
        Map<String,Integer> statusSummary = processingStatusRepository.summariseSubmissionStatus(submission.getId());

        //all submittables are complete if the summary of processing statuses has one status, and that status is 'Completed'
        return (statusSummary.size() == 1 && statusSummary.containsKey(ProcessingStatusEnum.Completed.name()));
    }

    public void markSubmissionAsCompleted(Submission submission){
        SubmissionStatus submissionStatus = submission.getSubmissionStatus();
        submissionStatus.setStatus(SubmissionStatusEnum.Completed);
        submissionStatusRepository.save(submissionStatus);
    }
}
