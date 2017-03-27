package uk.ac.ebi.subs.dispatcher;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.FullSubmission;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.FullSubmissionService;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.*;

@Service
public class DispatcherServiceImpl implements DispatcherService {


    private static final Logger logger = LoggerFactory.getLogger(DispatcherServiceImpl.class);

    @Override
    public Map<Archive, SubmissionEnvelope> assessDispatchReadiness(Submission submission) {

        FullSubmission fullSubmission = fullSubmissionService.fetchOne(submission.getId());

        /*
         * TODO this does not use the referenced sample information in supportingSamples
         */

        /*
        * this is a deliberately simple implementation for prototyping
        * we will need to redo this as we flesh out the system
        * */


        /*
         * for now, dispatch envelopes to one archive at a time
         */

        Map<Archive, Boolean> archiveProcessingRequired = new HashMap<>();
        Arrays.asList(Archive.values()).forEach(a -> archiveProcessingRequired.put(a, false));


        submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.streamBySubmissionId(submission.getId()))
                .filter(item ->
                        processingStatusesToAllow.contains(item.getProcessingStatus().getStatus()))
                .forEach(item -> {
                    archiveProcessingRequired.put(item.getArchive(), true);
                });


        Archive targetArchive = null;

        if (archiveProcessingRequired.get(Archive.BioSamples)) {
            targetArchive = Archive.BioSamples;
        } else if (archiveProcessingRequired.get(Archive.Ena)) {
            targetArchive = Archive.Ena;
        } else if (archiveProcessingRequired.get(Archive.ArrayExpress)) {
            targetArchive = Archive.ArrayExpress;
        }

        Map<Archive, SubmissionEnvelope> readyToDispatch = new HashMap<>();


        if (targetArchive == null) {
            logger.info("no work to do on submission {}", submission.getId());
        } else {
            SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(fullSubmission);
            readyToDispatch.put(targetArchive, submissionEnvelope);
        }

        return readyToDispatch;
    }

    @Override
    public Map<Archive, SubmissionEnvelope> requestSupportingInformation(Submission submission) {
        FullSubmission fullSubmission = fullSubmissionService.fetchOne(submission.getId());

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(fullSubmission);

        determineSupportingInformationRequired(submissionEnvelope);

        if (submissionEnvelope.getSupportingSamplesRequired().isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Archive, SubmissionEnvelope> maps = new HashMap<>();

        maps.put(Archive.BioSamples, submissionEnvelope);

        return maps;
    }

    @Override
    public void updateSubmittablesStatusToSubmitted(Archive archive, SubmissionEnvelope submissionEnvelope) {

        String submissionId = submissionEnvelope.getSubmission().getId();

        submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.streamBySubmissionId(submissionId))
                .filter(item -> archive.equals(item.getArchive()))
                .filter(item ->
                        processingStatusesToAllow.contains(item.getProcessingStatus().getStatus()))
                .map(item -> item.getProcessingStatus())
                .forEach(status -> {
                    status.setStatus(ProcessingStatusEnum.Dispatched);
                    processingStatusRepository.save(status);
                });

    }

    @Override
    public SubmissionEnvelope inflateInitialSubmission(Submission submission) {
        FullSubmission fullSubmission = fullSubmissionService.fetchOne(submission.getId());

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(fullSubmission);

        uk.ac.ebi.subs.repository.model.Submission refreshedSubmission = submissionRepository.findOne(submission.getId());

        refreshedSubmission.getSubmissionStatus().setStatus(SubmissionStatusEnum.Processing);
        submissionStatusRepository.save(refreshedSubmission.getSubmissionStatus());


        return submissionEnvelope;
    }

    public void determineSupportingInformationRequired(SubmissionEnvelope submissionEnvelope) {
        List<Sample> samples = submissionEnvelope.getSubmission().getSamples();
        List<Assay> assays = submissionEnvelope.getSubmission().getAssays();
        Set<SampleRef> suppportingSamplesRequired = submissionEnvelope.getSupportingSamplesRequired();
        List<Sample> supportingSamples = submissionEnvelope.getSupportingSamples();

        for (Assay assay : assays) {
            for (SampleUse sampleUse : assay.getSampleUses()) {
                SampleRef sampleRef = sampleUse.getSampleRef();

                if (suppportingSamplesRequired.contains(sampleRef)) {
                    //skip the searching steps if the sample ref is already in the sample required set
                    continue;
                }

                //is the sample in the submission
                Sample s = sampleRef.findMatch(samples);

                if (s == null) {
                    //is the sample already in the supporting information
                    s = sampleRef.findMatch(supportingSamples);
                }

                if (s == null) {
                    // sample referenced is not in the supporting information and is not in the submission, need to fetch it
                    suppportingSamplesRequired.add(sampleRef);
                }

            }
        }
    }


    private List<Class<? extends StoredSubmittable>> submittablesClassList;
    private FullSubmissionService fullSubmissionService;
    private SubmissionRepository submissionRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private ProcessingStatusRepository processingStatusRepository;
    private List<SubmittableRepository<?>> submissionContentsRepositories;
    private Set<String> processingStatusesToAllow;

    public DispatcherServiceImpl(
            FullSubmissionService fullSubmissionService,
            SubmissionRepository submissionRepository,
            SubmissionStatusRepository submissionStatusRepository,
            ProcessingStatusRepository processingStatusRepository,
            List<Class<? extends StoredSubmittable>> submittablesClassList,
            List<SubmittableRepository<?>> submissionContentsRepositories

    ) {
        this.fullSubmissionService = fullSubmissionService;
        this.submissionRepository = submissionRepository;
        this.submissionStatusRepository = submissionStatusRepository;

        this.submittablesClassList = submittablesClassList;
        this.processingStatusRepository = processingStatusRepository;
        this.submissionContentsRepositories = submissionContentsRepositories;

        processingStatusesToAllow = new HashSet<>();
        processingStatusesToAllow.add(ProcessingStatusEnum.Draft.name());
        processingStatusesToAllow.add(ProcessingStatusEnum.Submitted.name());
    }

}
