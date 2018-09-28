package uk.ac.ebi.subs.processing.archiveassigner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.ac.ebi.subs.CoreProcessingApp;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.StudyRef;
import uk.ac.ebi.subs.data.component.Submitter;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.processing.archiveassignment.SubmissionArchiveAssignmentService;
import uk.ac.ebi.subs.repository.model.Analysis;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.DataType;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.Project;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Study;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.DataTypeRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AnalysisRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.ProjectRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;
import uk.ac.ebi.subs.repository.services.SubmissionHelperService;
import uk.ac.ebi.subs.repository.services.SubmittableHelperService;

import java.util.Arrays;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

import static uk.ac.ebi.subs.processing.utils.DataTypeBuilder.buildDataType;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = CoreProcessingApp.class)
public class ArchiveAssignmentTest {


    private Sample sample;
    private Study study;
    private Assay assay;
    private Project project;
    private Analysis analysis;

    private DataType proteomicsStudyType;
    private DataType sampleType;
    private DataType enaType;
    private DataType projectType;

    @Autowired
    private SubmissionArchiveAssignmentService submissionArchiveAssignmentService;

    @Autowired
    private DataTypeRepository dataTypeRepository;


    @Before
    public void setUp() {
        tearDown();
        Submission submission = submissionHelperService.createSubmission(team, submitter);
        proteomicsStudyType = buildDataType(Archive.Pride, dataTypeRepository);
        sampleType = buildDataType(Archive.BioSamples, dataTypeRepository);
        enaType = buildDataType(Archive.Ena, dataTypeRepository);
        projectType = buildDataType(Archive.BioStudies, dataTypeRepository);

        project = createProject("testProject", submission);
        sample = createSample("testSample", submission);
        study = createStudy("testStudy", submission, proteomicsStudyType);
        assay = createAssay("testAssay", submission, sample, study);
        analysis = createSeqVarAnalysis("testAnalysis", submission, study, sample);
        submissionArchiveAssignmentService.assignArchives(submission);
    }

    @After
    public void tearDown() {
        Stream.of(
                studyRepository,
                sampleRepository,
                assayRepository,
                processingStatusRepository,
                projectRepository,
                analysisRepository,
                dataTypeRepository).forEach(MongoRepository::deleteAll);
    }


    @Test
    public void givenSample_assignBioSamples() {
        String archive = extractArchive(sample);

        assertThat(archive, equalTo(Archive.BioSamples.name()));
    }

    @Test
    public void givenProject_assignBioStudies() {
        String archive = extractArchive(project);

        assertThat(archive, equalTo(Archive.BioStudies.name()));
    }

    @Test
    public void givenProteomicsStudy_assignPride() {
        String archive = extractArchive(study);

        assertThat(archive, equalTo(Archive.Pride.name()));
    }

    @Test
    public void givenProteomicsAssay_assignPride() {
        String archive = extractArchive(assay);

        assertThat(archive, equalTo(Archive.Pride.name()));

    }

    @Test
    public void givenSeqVarAnalysis_assignENA() {
        String archive = extractArchive(analysis);

        assertThat(archive, equalTo(Archive.Ena.name()));
    }

    private String extractArchive(StoredSubmittable storedSubmittable) {
        ProcessingStatus processingStatus = processingStatusRepository.findOne(storedSubmittable.getProcessingStatus().getId());
        return processingStatus.getArchive();
    }

    private Team team = Team.build("tester1");
    private Submitter submitter = Submitter.build("alice@test.ac.uk");

    public Sample createSample(String alias, Submission submission) {
        Sample s = new Sample();
        s.setAlias(alias);
        s.setSubmission(submission);
        s.setDataType(sampleType);
        submittableHelperService.uuidAndTeamFromSubmissionSetUp(s);
        submittableHelperService.processingStatusAndValidationResultSetUp(s);
        sampleRepository.save(s);
        return s;
    }

    public Study createStudy(String alias, Submission submission, DataType studyDataType) {
        Study s = new Study();
        s.setAlias(alias);
        s.setSubmission(submission);
        s.setProjectRef(null);
        s.setDataType(studyDataType);
        submittableHelperService.uuidAndTeamFromSubmissionSetUp(s);
        submittableHelperService.processingStatusAndValidationResultSetUp(s);
        studyRepository.save(s);
        return s;
    }

    public Assay createAssay(String alias, Submission submission, Sample sample, Study study) {
        Assay a = new Assay();
        a.setAlias(alias);
        a.setSubmission(submission);
        a.setDataType(study.getDataType());

        submittableHelperService.uuidAndTeamFromSubmissionSetUp(a);
        submittableHelperService.processingStatusAndValidationResultSetUp(a);
        a.setStudyRef((StudyRef) study.asRef());

        assayRepository.save(a);
        return a;

    }

    public Analysis createSeqVarAnalysis(String alias, Submission submission, Study study, Sample sample) {
        Analysis a = new Analysis();
        a.setAlias(alias);
        a.setSubmission(submission);
        a.setDataType(enaType);

        submittableHelperService.uuidAndTeamFromSubmissionSetUp(a);
        submittableHelperService.processingStatusAndValidationResultSetUp(a);
        a.setSampleRefs(Arrays.asList((SampleRef) sample.asRef()));
        a.setStudyRefs(Arrays.asList((StudyRef) study.asRef()));


        analysisRepository.save(a);

        return a;
    }

    public Project createProject(String alias, Submission submission) {
        Project project = new Project();
        project.setAlias(alias);
        project.setSubmission(submission);
        project.setDataType(projectType);

        submittableHelperService.uuidAndTeamFromSubmissionSetUp(project);
        submittableHelperService.processingStatusAndValidationResultSetUp(project);

        projectRepository.save(project);
        return project;
    }


    @Autowired
    private SubmissionHelperService submissionHelperService;

    @Autowired
    private SubmittableHelperService submittableHelperService;

    @Autowired
    private StudyRepository studyRepository;

    @Autowired
    private SampleRepository sampleRepository;

    @Autowired
    private AssayRepository assayRepository;

    @Autowired
    private ProcessingStatusRepository processingStatusRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AnalysisRepository analysisRepository;


}
