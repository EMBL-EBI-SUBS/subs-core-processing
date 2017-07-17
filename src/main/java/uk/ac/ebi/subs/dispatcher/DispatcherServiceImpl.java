package uk.ac.ebi.subs.dispatcher;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Submittable;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.RefLookupService;
import uk.ac.ebi.subs.repository.SubmissionEnvelopeService;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusBulkOperations;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DispatcherServiceImpl implements DispatcherService {


    private static final Logger logger = LoggerFactory.getLogger(DispatcherServiceImpl.class);


    private boolean containsAnyAllowedStatus(Map.Entry<String, Map<String, Integer>> typeStatusSummary) {
        for (String status : processingStatusesToAllow) {
            if (typeStatusSummary.getValue().containsKey(status) && typeStatusSummary.getValue().get(status) > 0)
                return true;
        }
        return false;
    }

    @Override
    public Map<Archive, SubmissionEnvelope> assessDispatchReadiness(final Submission submission) {
        Map<String, Set<String>> typesAndIdsToConsider = processingStatusRepository
                .summariseSubmissionTypesWithSubmittableIds(submission.getId(), processingStatusesToAllow);


        Map<Archive, SubmissionEnvelope> readyForDispatch = new HashMap<>();

        logger.info("Submission {} has data to process {}", submission.getId(), typesAndIdsToConsider.keySet());
        for (Map.Entry<String, Set<String>> typeAndIds : typesAndIdsToConsider.entrySet()) {
            logger.info("Submission {} has data to process for {}: {}", submission.getId(), typeAndIds.getKey(), typeAndIds.getValue().size());
        }

        Set<Archive> archivesToBlock = new HashSet<>();

        for (Map.Entry<String, Set<String>> typeAndIds : typesAndIdsToConsider.entrySet()) {
            String type = typeAndIds.getKey();

            if (submittableRepositoryMap.containsKey(type)) {
                SubmittableRepository submittableRepository = submittableRepositoryMap.get(type);

                for (String submittableId : typeAndIds.getValue()) {
                    StoredSubmittable submittable = submittableRepository.findOne(submittableId);
                    Archive archive = submittable.getArchive();


                    List<StoredSubmittable> referencedSubmittables = submittable
                            .refs()
                            .filter(ref -> ref != null)
                            .filter(ref -> ref.getAlias() != null || ref.getAccession() != null) //TODO this is because of empty refs as defaults
                            .map(ref -> refLookupService.lookupRef(ref))
                            .filter(referencedSubmittable -> !isForSameArchiveAndInSameSubmission(submission, archive, referencedSubmittable))
                            .collect(Collectors.toList());

                    Optional<StoredSubmittable> optionalBlockingSubmittable = referencedSubmittables.stream()
                            .filter(sub -> !sub.isAccessioned())
                            .findAny();

                    if (!optionalBlockingSubmittable.isPresent()) {
                        SubmissionEnvelope submissionEnvelope = upsertSubmissionEnvelope(
                                archive,
                                submission,
                                readyForDispatch
                        );


                        submissionEnvelopeStuffer.add(submissionEnvelope, submittable);
                        submissionEnvelopeStuffer.addAll(submissionEnvelope, referencedSubmittables);
                    }

                    if (optionalBlockingSubmittable.isPresent()) {
                        archivesToBlock.add(archive);
                    }
                }


            }
        }

        for (Archive archiveToBlock : archivesToBlock) {
            readyForDispatch.remove(archiveToBlock);
        }

        return readyForDispatch;
    }

    private boolean isBlockerForThisSubmittable(Submission submission, Archive archive, StoredSubmittable sub) {
        return !(sub.isAccessioned() ||
                isForSameArchiveAndInSameSubmission(submission, archive, sub));
    }

    private boolean isForSameArchiveAndInSameSubmission(Submission submission, Archive archive, StoredSubmittable sub) {
        Assert.notNull(sub.getSubmission().getId());
        Assert.notNull(submission.getId());
        Assert.notNull(sub.getArchive());
        Assert.notNull(archive);
        return sub.getSubmission().getId().equals(submission.getId())
                && sub.getArchive().equals(archive);
    }


    @Override
    public Map<Archive, SubmissionEnvelope> determineSupportingInformationRequired(Submission submission) {
        SubmissionEnvelope submissionEnvelope = submissionEnvelopeService.fetchOne(submission.getId());

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

        Stream<Submittable> submittables = submissionEnvelope
                .allSubmissionItemsStream()
                .filter(item -> archive.equals(item.getArchive()));

        processingStatusBulkOperations.updateProcessingStatus(
                processingStatusesToAllow,
                submittables,
                submissionEnvelope.getSubmission(),
                ProcessingStatusEnum.Dispatched
        );
    }

    // only inserting Assays' SampleRefs for now
    @Override
    public void insertReferencedSamples(SubmissionEnvelope submissionEnvelope) {
        Set<SampleRef> assaySampleRefs = submissionEnvelope.getAssays()
                .stream()
                .flatMap(assay -> assay.getSampleUses().stream())
                .map(SampleUse::getSampleRef)
                .collect(Collectors.toSet());

        submissionEnvelope.getSupportingSamples().addAll((Set<? extends Sample>) refLookupService.lookupRefs(assaySampleRefs));
    }

    public void determineSupportingInformationRequired(SubmissionEnvelope submissionEnvelope) {
        List<Sample> samples = submissionEnvelope.getSamples();
        List<Assay> assays = submissionEnvelope.getAssays();
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
                    // is the sample already in the USI db
                    s = (Sample) refLookupService.lookupRef(sampleRef);
                }

                if (s == null) {
                    // sample referenced is not in the supporting information, nor in the submission, nor in the USI db so need to fetch it
                    suppportingSamplesRequired.add(sampleRef);
                }

            }
        }

    }

    public static SubmissionEnvelope upsertSubmissionEnvelope(
            Archive archive,
            Submission submission,
            Map<Archive, SubmissionEnvelope> receiver) {

        if (!receiver.containsKey(archive)) {
            receiver.put(archive, new SubmissionEnvelope(submission));
        }
        return receiver.get(archive);

    }


    private Set<String> processingStatusesToAllow;
    private Map<String, SubmittableRepository> submittableRepositoryMap;
    private RefLookupService refLookupService;
    private SubmissionEnvelopeService submissionEnvelopeService;
    private ProcessingStatusBulkOperations processingStatusBulkOperations;
    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionEnvelopeStuffer submissionEnvelopeStuffer;

    public DispatcherServiceImpl(

            RefLookupService refLookupService,
            ProcessingStatusRepository processingStatusRepository,
            ProcessingStatusBulkOperations processingStatusBulkOperations,
            Map<Class<? extends StoredSubmittable>, SubmittableRepository<? extends StoredSubmittable>> submittableRepositoryMap,
            SubmissionEnvelopeStuffer submissionEnvelopeStuffer
    ) {

        this.submissionEnvelopeService = submissionEnvelopeService;
        this.refLookupService = refLookupService;
        this.processingStatusRepository = processingStatusRepository;
        this.processingStatusBulkOperations = processingStatusBulkOperations;
        this.submissionEnvelopeStuffer = submissionEnvelopeStuffer;

        processingStatusesToAllow = new HashSet<>();
        processingStatusesToAllow.add(ProcessingStatusEnum.Draft.name());
        processingStatusesToAllow.add(ProcessingStatusEnum.Submitted.name());

        this.submittableRepositoryMap = new HashMap<>();
        submittableRepositoryMap.entrySet().forEach(es ->
                this.submittableRepositoryMap.put(es.getKey().getSimpleName(), es.getValue())
        );
    }

}
