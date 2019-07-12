package uk.ac.ebi.subs.apisupport;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit4.SpringRunner;
import uk.ac.ebi.subs.TestCoreProcessingApp;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.util.MongoDBDependentTest;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = TestCoreProcessingApp.class)
@Category(MongoDBDependentTest.class)
public class ApiSupportServiceDeleteTest {

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

    private Submission submission;

    @Before
    public void buildUp(){
        tearDown();
        createSubmissionContents();
    }

    @Test
    public void deleteSubmissionContentsAndNotSubmission(){
        assertThat(submissionRepository.findOne(submissionId), notNullValue());

        apiSupportService.deleteSubmissionContents(submission);

        assertThat(sampleRepository.findBySubmissionId(submissionId), hasSize(0));
        assertThat(processingStatusRepository.findBySubmissionId(submissionId), hasSize(0));
        assertThat(submissionStatusRepository.findAll(new PageRequest(0, 1)).getTotalElements(), is(equalTo(0L)));
        assertThat(submissionRepository.findOne(submissionId), notNullValue());
    }

    @Test
    public void deleteSubmissionAndSubmissionContents(){
        submissionRepository.delete(submission);

        apiSupportService.deleteSubmissionContents(submission);
        assertThat(submissionRepository.findOne(submissionId), nullValue());

        assertThat(sampleRepository.findBySubmissionId(submissionId),hasSize(0));
        assertThat(processingStatusRepository.findBySubmissionId(submissionId),hasSize(0));
        assertThat(submissionStatusRepository.findAll(new PageRequest(0,1)).getTotalElements(), is(equalTo(0L)));
    }

    @After
    public void tearDown(){
        Stream.of(sampleRepository,submissionRepository,processingStatusRepository,submissionStatusRepository).forEach(
                repo -> repo.deleteAll()
        );
    }

    private void createSubmissionContents(){
        submission = new Submission();
        submission.setId(submissionId);
        submission.setSubmissionStatus(new SubmissionStatus());

        submissionStatusRepository.save(submission.getSubmissionStatus());
        submissionRepository.save(submission);

        Sample sample = new Sample();
        sample.setSubmission(submission);
        sample.setProcessingStatus(ProcessingStatus.createForSubmittable(sample));

        processingStatusRepository.save(sample.getProcessingStatus());
        sampleRepository.save(sample);
    }
}
