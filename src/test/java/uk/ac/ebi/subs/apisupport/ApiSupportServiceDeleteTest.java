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
import uk.ac.ebi.subs.ApiSupportApplication;
import uk.ac.ebi.subs.apisupport.utils.MongoDBDependentTest;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = ApiSupportApplication.class)
@Category(MongoDBDependentTest.class)
public class ApiSupportServiceDeleteTest {

    @Test
    public void doNotDeleteIfSubmissionStillPresent(){
        apiSupportService.deleteSubmissionContents(submission);

        Submission storedSubmission = submissionRepository.findOne(submissionId);

        assertThat(storedSubmission, notNullValue());

        assertThat(sampleRepository.findBySubmissionId(submissionId),hasSize(1));
        assertThat(processingStatusRepository.findBySubmissionId(submissionId),hasSize(1));
        assertThat(submissionStatusRepository.findAll(new PageRequest(0,1)).getTotalElements(), is(equalTo(1L)));

    }

    @Test
    public void doDeleteIfSubmissionDeleted(){
        submissionRepository.delete(submission);

        apiSupportService.deleteSubmissionContents(submission);

        Submission storedSubmission = submissionRepository.findOne(submissionId);

        assertThat(storedSubmission, nullValue());

        assertThat(sampleRepository.findBySubmissionId(submissionId),hasSize(0));
        assertThat(processingStatusRepository.findBySubmissionId(submissionId),hasSize(0));
        assertThat(submissionStatusRepository.findAll(new PageRequest(0,1)).getTotalElements(), is(equalTo(0L)));

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
        Stream.of(sampleRepository,submissionRepository,processingStatusRepository,submissionStatusRepository).forEach(
                repo -> repo.deleteAll()
        );
    }

    private Submission submission;

    @Before
    public void buildUp(){
        tearDown();
        createSubmissionContents();

    }

    private void createSubmissionContents(){
        submission = new Submission();
        submission.setId(submissionId);
        submission.setSubmissionStatus(new SubmissionStatus());

        submissionStatusRepository.save(submission.getSubmissionStatus());
        submissionRepository.save(submission);

        Sample sample = new Sample();
        sample.setSubmission(submission);
        ProcessingStatus.createForSubmittable(sample);

        processingStatusRepository.save(sample.getProcessingStatus());
        sampleRepository.save(sample);
    }


}
