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
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.processing.utils.MongoDBDependentTest;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Study;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.DataTypeRepository;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;
import uk.ac.ebi.subs.repository.services.SubmittableHelperService;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertThat;
import static uk.ac.ebi.subs.processing.utils.DataTypeBuilder.buildDataType;

@RunWith(SpringJUnit4ClassRunner.class)
@Category(MongoDBDependentTest.class)
@SpringBootTest(classes = CoreProcessingApp.class)
public class SubmissionEnvelopeServiceTest {

    @Autowired
    private SubmissionEnvelopeService submissionEnvelopeService;

    @Autowired
    private SubmissionRepository submissionRepository;
    @Autowired
    private StudyRepository studyRepository;
    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private DataTypeRepository dataTypeRepository;

    @Autowired
    private SubmittableHelperService submittableHelperService;

    private Submission submission;
    private Study study;
    private Sample sample;


    @Test
    public void testSubmissionContentsStream() {
        Set<StoredSubmittable> contents = submissionEnvelopeService.submissionContents(submission.getId()).collect(Collectors.toSet());

        assertThat(contents, containsInAnyOrder(study, sample));
    }


    private void clearDbs() {
        Stream.of(studyRepository, sampleRepository, submissionRepository, dataTypeRepository).forEach(repo -> repo.deleteAll());
    }

    @Before
    public void buildUp() {
        clearDbs();

        Team team = Team.build("test");

        submission = new Submission();
        submission.setTeam(team);
        submissionRepository.insert(submission);

        study = new Study();
        study.setSubmission(submission);
        study.setAlias("study");
        study.setDataType(buildDataType(Archive.Ena,dataTypeRepository, "enaStudies"));
        submittableHelperService.setupNewSubmittable(study);
        studyRepository.save(study);


        sample = new Sample();
        sample.setSubmission(submission);
        sample.setAlias("sample");
        sample.setDataType(buildDataType(Archive.BioSamples,dataTypeRepository, "samples"));
        submittableHelperService.setupNewSubmittable(sample);
        sampleRepository.save(sample);
    }

    @After
    public void tearDown() {
        clearDbs();
    }



}
