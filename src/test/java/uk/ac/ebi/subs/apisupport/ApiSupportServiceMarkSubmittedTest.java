package uk.ac.ebi.subs.apisupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.ApiSupportApplication;
import uk.ac.ebi.subs.MongoDBDependentTest;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApiSupportApplication.class)
@Category(MongoDBDependentTest.class)
public class ApiSupportServiceMarkSubmittedTest {

    @Test
    public void markDraftAsSubmitted(){
        apiSupportService.markContentsAsSubmitted(submission);

        ProcessingStatus expectSubmitted = processingStatusRepository.findOne(draftSample.getProcessingStatus().getId());

        assertThat(expectSubmitted.getStatus(),equalTo(ProcessingStatusEnum.Submitted.name()));

        ProcessingStatus expectDispatched = processingStatusRepository.findOne(dispatchedSample.getProcessingStatus().getId());

        assertThat(expectDispatched.getStatus(),equalTo(ProcessingStatusEnum.Dispatched.name()));
    }

    @Test
    public void abortMarkingDraftAsSubmitted(){
        submission.getSubmissionStatus().setStatus(SubmissionStatusEnum.Draft);
        submissionStatusRepository.save(submission.getSubmissionStatus());

        apiSupportService.markContentsAsSubmitted(submission);

        ProcessingStatus expectDraft = processingStatusRepository.findOne(draftSample.getProcessingStatus().getId());

        assertThat(expectDraft.getStatus(),equalTo(ProcessingStatusEnum.Draft.name()));
    }





    private String submissionId = "thisIsAFakeId";

    @Autowired private ApiSupportService apiSupportService;

    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private SubmissionRepository submissionRepository;

    @Autowired
    private ProcessingStatusRepository processingStatusRepository;

    @Autowired
    private SubmissionStatusRepository submissionStatusRepository;

    @After
    public void tearDown(){
        Stream.of(sampleRepository,submissionRepository,processingStatusRepository,submissionRepository,submissionStatusRepository).forEach(
                repo -> repo.deleteAll()
        );
    }

    private Submission submission;
    private Sample draftSample, dispatchedSample;

    @Before
    public void buildUp(){
        tearDown();
        createSubmissionContents();

    }

    private void createSubmissionContents(){
        submission = new Submission();
        submission.setId(submissionId);
        submission.setSubmissionStatus(new SubmissionStatus());
        submission.getSubmissionStatus().setStatus(SubmissionStatusEnum.Submitted);

        submissionStatusRepository.save(submission.getSubmissionStatus());
        submissionRepository.save(submission);

        draftSample = new Sample();
        draftSample.setSubmission(submission);
        ProcessingStatus.createForSubmittable(draftSample);

        processingStatusRepository.save(draftSample.getProcessingStatus());
        sampleRepository.save(draftSample);

        dispatchedSample = new Sample();
        dispatchedSample.setSubmission(submission);
        ProcessingStatus.createForSubmittable(dispatchedSample);
        dispatchedSample.getProcessingStatus().setStatus(ProcessingStatusEnum.Dispatched);

        processingStatusRepository.save(dispatchedSample.getProcessingStatus());
        sampleRepository.save(dispatchedSample);
    }


}
