package uk.ac.ebi.subs.apisupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.CoreProcessingApp;
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
import uk.ac.ebi.subs.util.MongoDBDependentTest;

import java.util.Optional;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CoreProcessingApp.class)
@Category(MongoDBDependentTest.class)
public class ApiSupportServiceMarkSubmittedTest {

    @Test
    public void markDraftAsSubmitted(){
        apiSupportService.markContentsAsSubmitted(submission);

        ProcessingStatus expectSubmitted = processingStatusRepository.findOne(draftSample.getProcessingStatus().getId());

        assertNotNull(expectSubmitted);
        assertThat(expectSubmitted.getStatus(),equalTo(ProcessingStatusEnum.Submitted.name()));

        ProcessingStatus expectDispatched = processingStatusRepository.findOne(dispatchedSample.getProcessingStatus().getId());

        assertNotNull(expectDispatched);
        assertThat(expectDispatched.getStatus(),equalTo(ProcessingStatusEnum.Dispatched.name()));
    }

    @Test
    public void abortMarkingDraftAsSubmitted(){
        submission.getSubmissionStatus().setStatus(SubmissionStatusEnum.Draft);
        submissionStatusRepository.save(submission.getSubmissionStatus());

        apiSupportService.markContentsAsSubmitted(submission);

        ProcessingStatus expectDraft = processingStatusRepository.findOne(draftSample.getProcessingStatus().getId());

        assertNotNull(expectDraft);
        assertThat(expectDraft.getStatus(),equalTo(ProcessingStatusEnum.Draft.name()));
    }

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
                CrudRepository::deleteAll
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
        String submissionId = "thisIsAFakeId";
        submission.setId(submissionId);
        submission.setSubmissionStatus(new SubmissionStatus());
        submission.getSubmissionStatus().setStatus(SubmissionStatusEnum.Submitted);

        submissionStatusRepository.save(submission.getSubmissionStatus());
        submissionRepository.save(submission);

        draftSample = new Sample();
        draftSample.setSubmission(submission);
        draftSample.setProcessingStatus(ProcessingStatus.createForSubmittable(draftSample));

        processingStatusRepository.save(draftSample.getProcessingStatus());
        sampleRepository.save(draftSample);

        dispatchedSample = new Sample();
        dispatchedSample.setSubmission(submission);
        dispatchedSample.setProcessingStatus(ProcessingStatus.createForSubmittable(dispatchedSample));
        dispatchedSample.getProcessingStatus().setStatus(ProcessingStatusEnum.Dispatched);

        processingStatusRepository.save(dispatchedSample.getProcessingStatus());
        sampleRepository.save(dispatchedSample);
    }


}
