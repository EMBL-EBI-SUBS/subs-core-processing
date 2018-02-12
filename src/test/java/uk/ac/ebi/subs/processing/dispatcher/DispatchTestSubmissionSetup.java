package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import uk.ac.ebi.subs.data.component.*;
import uk.ac.ebi.subs.repository.model.*;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;
import uk.ac.ebi.subs.repository.services.SubmissionHelperService;
import uk.ac.ebi.subs.repository.services.SubmittableHelperService;

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
    private ProcessingStatusRepository processingStatusRepository;

    private Team team = Team.build("tester1");
    private Submitter submitter =  Submitter.build("alice@test.ac.uk");

    public DispatchTestSubmissionSetup(SubmissionRepository submissionRepository, SubmissionHelperService submissionHelperService, SubmittableHelperService submittableHelperService, SampleRepository sampleRepository, StudyRepository studyRepository, AssayRepository assayRepository, ProcessingStatusRepository processingStatusRepository) {
        this.submissionRepository = submissionRepository;
        this.submissionHelperService = submissionHelperService;
        this.submittableHelperService = submittableHelperService;
        this.sampleRepository = sampleRepository;
        this.studyRepository = studyRepository;
        this.assayRepository = assayRepository;
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

    private void setArchive(StoredSubmittable submittable, Archive archive){
        Assert.notNull(archive);
        Assert.notNull(submittable.getProcessingStatus());
        Assert.notNull(submittable.getId());

        submittable.getProcessingStatus().setArchive(archive.name());
        processingStatusRepository.save(submittable.getProcessingStatus());

    }

}
