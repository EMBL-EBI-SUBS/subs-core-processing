package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.File;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.component.StudyRef;
import uk.ac.ebi.subs.data.component.Submitter;
import uk.ac.ebi.subs.data.component.Team;
import uk.ac.ebi.subs.repository.model.Assay;
import uk.ac.ebi.subs.repository.model.AssayData;
import uk.ac.ebi.subs.repository.model.Sample;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Study;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.fileupload.FileRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayDataRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;
import uk.ac.ebi.subs.repository.services.SubmissionHelperService;
import uk.ac.ebi.subs.repository.services.SubmittableHelperService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

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
    private ProcessingStatusRepository processingStatusRepository;
    private FileRepository fileRepository;

    private Team team = Team.build("tester1");
    private Submitter submitter =  Submitter.build("alice@test.ac.uk");

    public DispatchTestSubmissionSetup(SubmissionRepository submissionRepository, SubmissionHelperService submissionHelperService,
                                       SubmittableHelperService submittableHelperService, SampleRepository sampleRepository,
                                       StudyRepository studyRepository, AssayRepository assayRepository,
                                       ProcessingStatusRepository processingStatusRepository,
                                       AssayDataRepository assayDataRepository, FileRepository fileRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionHelperService = submissionHelperService;
        this.submittableHelperService = submittableHelperService;
        this.sampleRepository = sampleRepository;
        this.studyRepository = studyRepository;
        this.assayRepository = assayRepository;
        this.assayDataRepository = assayDataRepository;
        this.fileRepository = fileRepository;
        this.processingStatusRepository = processingStatusRepository;
    }

    public void clearRepos(){
        Stream.of(
               sampleRepository,studyRepository,assayRepository,submissionRepository, processingStatusRepository
        ).forEach(
                repo -> repo.deleteAll()
        );
    }

    public Submission createSubmission(){
        return submissionHelperService.createSubmission(team,submitter);
    }

    public Sample createSample(String alias, Submission submission){
        Sample s = new Sample();
        s.setAlias(alias);
        s.setSubmission(submission);
        submittableHelperService.setupNewSubmittable(s);
        setArchive(s,Archive.BioSamples);
        sampleRepository.save(s);
        return s;
    }

    public Study createStudy(String alias, Submission submission){
        Study s = new Study();
        s.setAlias(alias);
        s.setSubmission(submission);
        s.setProjectRef(null);
        submittableHelperService.setupNewSubmittable(s);
        setArchive(s,Archive.Ena);
        studyRepository.save(s);
        return s;
    }

    public Assay createAssay(String alias, Submission submission, Sample sample, Study study){
        Assay a = new Assay();
        a.setAlias(alias);
        a.setSubmission(submission);

        submittableHelperService.setupNewSubmittable(a);
        setArchive(a,Archive.Ena);
        a.setStudyRef((StudyRef) study.asRef());
        a.getSampleUses().add(new SampleUse((SampleRef) sample.asRef()));

        assayRepository.save(a);
        return a;
    }

    AssayData createAssayDataWithNbOfFiles(String alias, Submission submission, int nbOfFiles) {
        AssayData assayData = new AssayData();
        assayData.setAlias(alias);
        assayData.setSubmission(submission);

        submittableHelperService.setupNewSubmittable(assayData);
        setArchive(assayData, Archive.Ena);

        List<File> fileRefs = createFileRefs(nbOfFiles, alias, submission.getId());
        assayData.setFiles(fileRefs);

        assayDataRepository.save(assayData);

        return assayData;
    }

    private void setArchive(StoredSubmittable submittable, Archive archive){
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
