package uk.ac.ebi.subs.processing.dispatcher;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.StopWatch;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.processing.utils.MongoDBDependentTest;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Study;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.Assert.assertThat;

/**
 * Created by davidr on 07/07/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@Category(MongoDBDependentTest.class)
@SpringBootTest(classes = CoreProcessingApp.class)
public class DispatchReadinessTest {


    @Autowired
    private DispatchTestSubmissionSetup dispatchTestSubmissionSetup;

    @Autowired
    private ProcessingStatusRepository processingStatusRepository;

    @Autowired
    private DispatcherService dispatcherService;

    @Autowired
    private SampleRepository sampleRepository;

    private Submission submission;
    private Study study;
    private List<Assay> assays;
    private List<Sample> samples;
    private static final int SAMPLE_AND_ASSAY_COUNT = 10;
    private static String JWT_TOKEN = "testTokenText";


    @Before
    public void buildUp() {
        dispatchTestSubmissionSetup.clearRepos();

        dispatchTestSubmissionSetup.createSubmission();

        submission = dispatchTestSubmissionSetup.createSubmission();
        study = dispatchTestSubmissionSetup.createStudy("test-study", submission);

        samples = IntStream
                .rangeClosed(1, SAMPLE_AND_ASSAY_COUNT)
                .mapToObj(i -> Integer.valueOf(i))
                .map(i -> dispatchTestSubmissionSetup.createSample(i.toString(), submission))
                .collect(Collectors.toList());

        assays = IntStream
                .rangeClosed(1, SAMPLE_AND_ASSAY_COUNT)
                .mapToObj(i -> Integer.valueOf(i))
                .map(i -> dispatchTestSubmissionSetup.createAssay(i.toString(), submission, samples.get(i - 1), study))
                .collect(Collectors.toList());

        ProcessingStatusEnum statusEnum = ProcessingStatusEnum.Submitted;

        List<StoredSubmittable> submittables = new LinkedList<>();
        submittables.add(study);
        submittables.addAll(samples);
        submittables.addAll(assays);

        submittables.forEach(s -> changeSubmittableStatus(statusEnum, s));

    }

    private void changeSubmittableStatus(ProcessingStatusEnum statusEnum, StoredSubmittable s) {
        ProcessingStatus ps = s.getProcessingStatus();
        ps.setStatus(statusEnum);
        processingStatusRepository.save(ps);
    }

    private void complete(Sample s) {
        s.setAccession(s.getAlias());
        sampleRepository.save(s);
        changeSubmittableStatus(ProcessingStatusEnum.Completed, s);
    }

    @After
    public void tearDown() {
        dispatchTestSubmissionSetup.clearRepos();
    }

    @Test
    public void testSampleReadiness() {
        StopWatch stopWatch = new StopWatch();

        stopWatch.start("sample dispatch test");

        Map<Archive, SubmissionEnvelope> dispatcherOutput = dispatcherService.assessDispatchReadiness(submission, JWT_TOKEN);

        stopWatch.stop();
        System.out.println(stopWatch.prettyPrint());


        assertThat(dispatcherOutput.keySet(), hasSize(1));
        assertThat(dispatcherOutput, hasKey(Archive.BioSamples));


        SubmissionEnvelope submissionEnvelope = dispatcherOutput.get(Archive.BioSamples);

        assertThat(
                submissionEnvelope.getSamples(), hasSize(SAMPLE_AND_ASSAY_COUNT)
        );
    }

    @Test
    public void testAssayArchiveReadiness() {
        samples.forEach(s -> complete(s));

        StopWatch stopWatch = new StopWatch();

        stopWatch.start("assay archive dispatch test");

        Map<Archive, SubmissionEnvelope> dispatcherOutput = dispatcherService.assessDispatchReadiness(submission, JWT_TOKEN);

        stopWatch.stop();
        System.out.println(stopWatch.prettyPrint());


        assertThat(dispatcherOutput.keySet(), hasSize(1));
        assertThat(dispatcherOutput, hasKey(Archive.Ena));


        SubmissionEnvelope submissionEnvelope = dispatcherOutput.get(Archive.Ena);

        assertThat(
                submissionEnvelope.getSamples(), hasSize(SAMPLE_AND_ASSAY_COUNT)
        );
        assertThat(
                submissionEnvelope.getAssays(), hasSize(SAMPLE_AND_ASSAY_COUNT)
        );
        assertThat(
                submissionEnvelope.getStudies(), hasSize(1)
        );
    }
}
