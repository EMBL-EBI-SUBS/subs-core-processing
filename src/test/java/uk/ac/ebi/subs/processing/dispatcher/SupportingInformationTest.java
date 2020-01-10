package uk.ac.ebi.subs.processing.dispatcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.component.Submitter;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.util.MongoDBDependentTest;

import java.util.Map;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

@RunWith(SpringJUnit4ClassRunner.class)
@Category(MongoDBDependentTest.class)
@SpringBootTest(classes = CoreProcessingApp.class)
public class SupportingInformationTest {

    @Autowired
    DispatcherService dispatcherService;

    @Autowired
    private SubmissionEnvelopeService submissionEnvelopeService;

    @Autowired
    SubmissionRepository submissionRepository;

    @Autowired
    SubmissionStatusRepository submissionStatusRepository;

    @Autowired
    AssayRepository assayRepository;

    Submission submission;
    Assay assay;

    @Before
    public void buildUp() {
        tearDown();

        submission = new Submission();
        submission.setSubmissionStatus(new SubmissionStatus(SubmissionStatusEnum.Draft));

        submission.setTeam(Team.build("testerTeam"));
        submission.setSubmitter(Submitter.build("bob@test.ac.uk"));

        submissionStatusRepository.save(submission.getSubmissionStatus());
        submissionRepository.save(submission);

        assay = new Assay();
        assay.setAlias("bob");

        SampleRef sampleRef = new SampleRef();
        //TODO sampleRef.setArchive(Archive.BioSamples.name());
        sampleRef.setAlias("bob");
        sampleRef.setAlias("S1");

        assay.setSubmission(submission);
        assay.getSampleUses().add(new SampleUse(sampleRef));

        assayRepository.save(assay);
    }

    @After
    public void tearDown() {
        Stream.of(submissionRepository, submissionStatusRepository, assayRepository).forEach(repo -> repo.deleteAll());
    }

    @Test
    public void testSupportingSamples() {
        SubmissionEnvelope submissionEnvelope = submissionEnvelopeService.fetchOne(submission.getId());
        Map<Archive, SubmissionEnvelope> requests = dispatcherService.determineSupportingInformationRequired(submissionEnvelope);

        assertThat(requests.keySet(), hasSize(1));
        assertThat(requests.containsKey(Archive.BioSamples), is(true));


        SubmissionEnvelope envelope = requests.get(Archive.BioSamples);

        assertThat("supporting info requirement identified ", envelope.getSupportingSamplesRequired(), hasSize(1));
        assertThat("supporting info not filled out yet", envelope.getSupportingSamples(), empty());
    }
}
