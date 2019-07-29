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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This is a Spring @Service component for {@link Submission} entity, that is dealing with {@link Submission} completion.
 */
@Service
public class SubmissionCompletionService {

    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private SubmissionRepository submissionRepository;

    private final static List<String> SUCCEED_PROCESSINGSTATUSES =
            Arrays.asList(ProcessingStatusEnum.Completed.name(), ProcessingStatusEnum.ArchiveDisabled.name());
    private final static List<String> ERRED_PROCESSINGSTATUSES =
            Arrays.asList(ProcessingStatusEnum.Rejected.name(), ProcessingStatusEnum.Error.name());
    private final static List<String> FINISHED_PROCESSINGSTATUSES =
            Stream.concat(SUCCEED_PROCESSINGSTATUSES.stream(), ERRED_PROCESSINGSTATUSES.stream()).collect(Collectors.toList());

    public SubmissionCompletionService(ProcessingStatusRepository processingStatusRepository,
                                       SubmissionStatusRepository submissionStatusRepository,
                                       SubmissionRepository submissionRepository) {
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
        this.submissionRepository = submissionRepository;
    }

    public boolean allSubmittablesProcessingFinished(String submissionId) {
        Map<String, Integer> statusSummary = getSubmissionStatusSummary(submissionId);

        for(String statusKey : statusSummary.keySet()) {
            if (!FINISHED_PROCESSINGSTATUSES.contains(statusKey)) {
                return false;
            }
        }

        return true;
    }

    public void markSubmissionWithFinishedStatus(String submissionId){
        Submission submission = submissionRepository.findOne(submissionId);
        SubmissionStatus submissionStatus = submission.getSubmissionStatus();
        if (allSubmittablesSucceed(submissionId)) {
            submissionStatus.setStatus(SubmissionStatusEnum.Completed);
        } else {
            submissionStatus.setStatus(SubmissionStatusEnum.Failed);
        }
        submissionStatusRepository.save(submissionStatus);
    }

    private Map<String, Integer> getSubmissionStatusSummary(String submissionId) {
        return processingStatusRepository.summariseSubmissionStatus(submissionId);
    }

    private boolean allSubmittablesSucceed(String submissionId) {
        Map<String, Integer> statusSummary = getSubmissionStatusSummary(submissionId);

        return Collections.disjoint(statusSummary.keySet(), ERRED_PROCESSINGSTATUSES);
    }
}
