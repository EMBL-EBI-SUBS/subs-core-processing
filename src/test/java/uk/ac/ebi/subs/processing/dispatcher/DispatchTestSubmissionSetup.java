package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.File;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.component.StudyRef;
import uk.ac.ebi.subs.data.component.Submitter;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.repository.model.Analysis;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.AssayData;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Study;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.DataTypeRepository;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.fileupload.FileRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AnalysisRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayDataRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;
import uk.ac.ebi.subs.repository.services.SubmissionHelperService;
import uk.ac.ebi.subs.repository.services.SubmittableHelperService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static uk.ac.ebi.subs.processing.utils.DataTypeBuilder.buildDataType;

/**
 * Created by davidr on 07/07/2017.
 */
@Component
public class DispatchTestSubmissionSetup {

    private SubmissionRepository submissionRepository;
    private SubmissionHelperService submissionHelperService;
    private SubmittableHelperService submittableHelperService;
    private SampleRepository sampleRepository;
    private StudyRepository studyRepository;
    private AssayRepository assayRepository;
    private AssayDataRepository assayDataRepository;
    private AnalysisRepository analysisRepository;
    private ProcessingStatusRepository processingStatusRepository;
    private FileRepository fileRepository;
    private DataTypeRepository dataTypeRepository;

    private Team team = Team.build("tester1");
    private Submitter submitter = Submitter.build("alice@test.ac.uk");

    public DispatchTestSubmissionSetup(SubmissionRepository submissionRepository, SubmissionHelperService submissionHelperService, SubmittableHelperService submittableHelperService, SampleRepository sampleRepository, StudyRepository studyRepository, AssayRepository assayRepository, AssayDataRepository assayDataRepository, AnalysisRepository analysisRepository, ProcessingStatusRepository processingStatusRepository, FileRepository fileRepository, DataTypeRepository dataTypeRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionHelperService = submissionHelperService;
        this.submittableHelperService = submittableHelperService;
        this.sampleRepository = sampleRepository;
        this.studyRepository = studyRepository;
        this.assayRepository = assayRepository;
        this.assayDataRepository = assayDataRepository;
        this.analysisRepository = analysisRepository;
        this.processingStatusRepository = processingStatusRepository;
        this.fileRepository = fileRepository;
        this.dataTypeRepository = dataTypeRepository;
    }

    public void clearRepos() {
        Stream.of(
                submissionRepository,
                sampleRepository,
                studyRepository,
                assayRepository,
                assayDataRepository,
                fileRepository,
                processingStatusRepository,
                analysisRepository,
                dataTypeRepository
        ).forEach(
                CrudRepository::deleteAll
        );
    }

    public Submission createSubmission() {
        return submissionHelperService.createSubmission(team, submitter);
    }

    public Sample createSample(String alias, Submission submission) {
        Sample s = new Sample();
        s.setAlias(alias);
        s.setSubmission(submission);
        s.setDataType(buildDataType(Archive.BioSamples, dataTypeRepository));
        submittableHelperService.setupNewSubmittable(s);
        setArchive(s, Archive.BioSamples);
        sampleRepository.save(s);
        return s;
    }

    public Study createStudy(String alias, Submission submission) {
        Study s = new Study();
        s.setAlias(alias);
        s.setSubmission(submission);
        s.setProjectRef(null);
        s.setDataType(buildDataType(Archive.Ena, dataTypeRepository));
        submittableHelperService.setupNewSubmittable(s);
        setArchive(s, Archive.Ena);
        studyRepository.save(s);
        return s;
    }

    public Assay createAssay(String alias, Submission submission, Sample sample, Study study) {
        Assay a = new Assay();
        a.setAlias(alias);
        a.setSubmission(submission);
        a.setDataType(buildDataType(Archive.Ena, dataTypeRepository));
        submittableHelperService.setupNewSubmittable(a);
        setArchive(a, Archive.Ena);
        a.setStudyRef((StudyRef) study.asRef());
        a.getSampleUses().add(new SampleUse((SampleRef) sample.asRef()));

        assayRepository.save(a);
        return a;
    }

    AssayData createAssayDataWithNbOfFiles(String alias, Submission submission, int nbOfFiles) {
        AssayData assayData = new AssayData();
        assayData.setAlias(alias);
        assayData.setSubmission(submission);
        assayData.setDataType(buildDataType(Archive.Ena, dataTypeRepository));
        submittableHelperService.setupNewSubmittable(assayData);
        setArchive(assayData, Archive.Ena);

        List<File> fileRefs = createFileRefs(nbOfFiles, alias, submission.getId());
        assayData.setFiles(fileRefs);

        assayDataRepository.save(assayData);

        return assayData;
    }

    Analysis createAnalysisWithNbOfFiles(String alias, Submission submission, int nbOfFiles) {
        Analysis analysis = new Analysis();
        analysis.setAlias(alias);
        analysis.setDataType(buildDataType(Archive.Ena,dataTypeRepository));
        analysis.setSubmission(submission);

        submittableHelperService.setupNewSubmittable(analysis);
        setArchive(analysis, Archive.Ena);

        List<File> fileRefs = createFileRefs(nbOfFiles, alias, submission.getId());
        analysis.setFiles(fileRefs);

        analysisRepository.save(analysis);

        return analysis;
    }

    private void setArchive(StoredSubmittable submittable, Archive archive) {
        Assert.notNull(archive);
        Assert.notNull(submittable.getProcessingStatus());
        Assert.notNull(submittable.getId());

        submittable.getProcessingStatus().setArchive(archive.name());
        processingStatusRepository.save(submittable.getProcessingStatus());

    }

    private List<File> createFileRefs(int nbOfFiles, String filenamePrefix, String submissionId) {
        List<File> fileRefs = new ArrayList<>();
        final String checksum = "ABCD1234EFGH5678";

        for (int i = 0; i < nbOfFiles; i++) {
            File file = new File();
            String filename = filenamePrefix + i;
            file.setName(filename);
            file.setType("CRAM");
            file.setChecksumMethod("MD5");
            file.setChecksum(checksum);

            fileRefs.add(file);

            createUploadedFile(filename, submissionId, checksum);
        }

        return fileRefs;
    }

    private void createUploadedFile(String filename, String submissionId, String checksum) {
        uk.ac.ebi.subs.repository.model.fileupload.File uploadedFile = new uk.ac.ebi.subs.repository.model.fileupload.File();
        uploadedFile.setFilename(filename);
        uploadedFile.setTargetPath("/dummy/target/path/" + filename);
        uploadedFile.setSubmissionId(submissionId);
        uploadedFile.setTotalSize(12345678l);
        uploadedFile.setChecksum(checksum);

        fileRepository.save(uploadedFile);
    }

//                        uploadedFile.setChecksum(file.getChecksum());

}
