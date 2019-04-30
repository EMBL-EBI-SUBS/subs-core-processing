package uk.ac.ebi.subs.processing.dispatcher;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;

import java.util.HashMap;
import java.util.Map;

@RunWith(SpringRunner.class)
public class SubmissionCompletionServiceTest {

    private SubmissionCompletionService submissionCompletionService;

    private ProcessingStatusRepository mockProcessingStatusRepository;
    private SubmissionStatusRepository mockSubmissionStatusRepository;
    private SubmissionRepository mockSubmissionRepository;

    private Submission submission;
    private SubmissionStatus submissionStatus;

    private Map<String,Integer> statusSummary;

    @Before
    public void buildUp() {
        submissionStatus = new SubmissionStatus();
        submission = new Submission();
        submission.setId("foo");
        submission.setSubmissionStatus(submissionStatus);

        mockProcessingStatusRepository = Mockito.mock(ProcessingStatusRepository.class);
        mockSubmissionStatusRepository = Mockito.mock(SubmissionStatusRepository.class);
        mockSubmissionRepository = Mockito.mock(SubmissionRepository.class);

        submissionCompletionService = new SubmissionCompletionService(
                mockProcessingStatusRepository,
                mockSubmissionStatusRepository,
                mockSubmissionRepository
        );

        statusSummary = new HashMap<>();
    }

    @Test
    public void all_statuses_completed(){
        statusSummary.put(ProcessingStatusEnum.Completed.name(),10);

        Mockito.when(mockProcessingStatusRepository.summariseSubmissionStatus(submission.getId()))
                .thenReturn(statusSummary);

        Assert.assertTrue(submissionCompletionService.allSubmittablesCompleted(submission.getId()));
    }

    @Test
    public void all_statuses_draft(){
        statusSummary.put(ProcessingStatusEnum.Draft.name(),10);

        Mockito.when(mockProcessingStatusRepository.summariseSubmissionStatus(submission.getId()))
                .thenReturn(statusSummary);

        Assert.assertFalse(submissionCompletionService.allSubmittablesCompleted(submission.getId()));
    }

    @Test
    public void some_statuses_completed(){
        statusSummary.put(ProcessingStatusEnum.Completed.name(),10);
        statusSummary.put(ProcessingStatusEnum.Draft.name(),10);

        Mockito.when(mockProcessingStatusRepository.summariseSubmissionStatus(submission.getId()))
                .thenReturn(statusSummary);

        Assert.assertFalse(submissionCompletionService.allSubmittablesCompleted(submission.getId()));
    }

    @Test
    public void submission_marked_as_completed(){
        Mockito.when(mockSubmissionRepository.findOne(submission.getId()))
                .thenReturn(submission);

        submissionCompletionService.markSubmissionAsCompleted(submission.getId());

        Mockito.verify(mockSubmissionStatusRepository).save(submissionStatus);
        Assert.assertEquals(SubmissionStatusEnum.Completed.name(),submissionStatus.getStatus());

    }
}
