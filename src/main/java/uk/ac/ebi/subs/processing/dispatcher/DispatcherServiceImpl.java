package uk.ac.ebi.subs.processing.dispatcher;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.AbstractSubsRef;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.data.component.SampleRef;
import uk.ac.ebi.subs.data.component.SampleUse;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.submittable.Analysis;
import uk.ac.ebi.subs.data.submittable.Assay;
import uk.ac.ebi.subs.data.submittable.AssayData;
import uk.ac.ebi.subs.data.submittable.Sample;
import uk.ac.ebi.subs.data.submittable.Submittable;
import uk.ac.ebi.subs.error.EntityNotFoundException;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.processing.fileupload.UploadedFile;
import uk.ac.ebi.subs.repository.RefLookupService;
import uk.ac.ebi.subs.repository.config.SubmittableConfig;
import uk.ac.ebi.subs.repository.model.DataType;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.fileupload.File;
import uk.ac.ebi.subs.repository.repos.fileupload.FileRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusBulkOperations;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class DispatcherServiceImpl implements DispatcherService {


    private static final Logger logger = LoggerFactory.getLogger(DispatcherServiceImpl.class);

    @Override
    /**
     * When is a submittable ready for dispatch?
     *
     * If all the things it references are either
     *  - destined for the same archive as it, and in the same submission (archive agent can resolve accessioning + linking)
     *  - been accessioned already
     *
     * If any of the submittables for an archive are not ready for dispatch, don't send anything to that archive
     *
     */
    public Map<Archive, SubmissionEnvelope> assessDispatchReadiness(final Submission submission, final String jwtToken) {
        final String submissionId = submission.getId();
        Map<String, Set<String>> typesAndIdsToConsider = new HashMap<>();

        for (Map.Entry<DataType, Set<String>> es : processingStatusRepository
                .summariseDataTypesWithSubmittableIds(submissionId, processingStatusesToAllow).entrySet()) {
            typesAndIdsToConsider.put(es.getKey().getId(), es.getValue());
        }

        Map<Archive, SubmissionEnvelope> readyForDispatch = new HashMap<>();

        logger.info("Submission {} has data to process {}", submissionId, typesAndIdsToConsider.keySet());
        for (Map.Entry<String, Set<String>> typeAndIds : typesAndIdsToConsider.entrySet()) {
            logger.info("Submission {} has data to process for {}: {}", submissionId, typeAndIds.getKey(), typeAndIds.getValue().size());
        }

        Set<Archive> archivesToBlock = new HashSet<>();

        //expect many refs to the same thing, e.g. all assays pointing to the same study
        Map<AbstractSubsRef,StoredSubmittable> refLookupCache = new HashMap<>();

        for (Map.Entry<String, Set<String>> typeAndIds : typesAndIdsToConsider.entrySet()) {
            String type = typeAndIds.getKey();

            if (dataTypeRepositoryMap.containsKey(type)) {
                SubmittableRepository submittableRepository = dataTypeRepositoryMap.get(type);

                for (String submittableId : typeAndIds.getValue()) {
                    final Optional<?> submittableById = Optional.ofNullable(submittableRepository.findOne(submittableId));
                    StoredSubmittable submittable = (StoredSubmittable) submittableById
                        .orElseThrow(() -> new EntityNotFoundException(
                            String.format("Submittable entity with ID: %s is not found in the database.", submittableId)));
                    Archive archive = Archive.valueOf(submittable.getProcessingStatus().getArchive());

                    List<StoredSubmittable> referencedSubmittables = submittable
                            .refs()
                            .filter(Objects::nonNull)
                            .filter(ref -> ref.getAlias() != null || ref.getAccession() != null) //TODO this is because of empty refs as defaults
                            .map(ref -> lookupRefAndFillInAccession(refLookupCache, ref) )
                            .filter(Objects::nonNull)
                            .filter(referencedSubmittable -> !isForSameArchiveAndInSameSubmission(submissionId, archive, referencedSubmittable))
                            .collect(Collectors.toList());

                    Optional<StoredSubmittable> optionalBlockingSubmittable = referencedSubmittables.stream()
                            .filter(sub -> !sub.isAccessioned())
                            .findAny();

                    if (!optionalBlockingSubmittable.isPresent()) {
                        SubmissionEnvelope submissionEnvelope = readyForDispatch.get(archive);
                        if (submissionEnvelope == null) {
                            submissionEnvelope = new SubmissionEnvelope(submission);
                            readyForDispatch.put(archive, submissionEnvelope);
                        }
                        submissionEnvelope.setJWTToken(jwtToken);

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

    private StoredSubmittable lookupRefAndFillInAccession(Map<AbstractSubsRef, StoredSubmittable> refLookupCache, AbstractSubsRef ref) {
        if (!refLookupCache.containsKey(ref)) {
            refLookupCache.put(ref, refLookupService.lookupRef(ref));
        }

        StoredSubmittable ss = refLookupCache.get(ref);

        if (ss != null && !ref.isAccessioned() && ss.isAccessioned()){
            ref.setAccession(ss.getAccession());
        }

        return ss;
    }

    private boolean isForSameArchiveAndInSameSubmission(String submissionId, Archive archive, StoredSubmittable sub) {
        Assert.notNull(sub.getSubmission().getId());
        Assert.notNull(submissionId);
        Assert.notNull(sub.getProcessingStatus().getArchive());
        Assert.notNull(archive);
        return sub.getSubmission().getId().equals(submissionId)
                && sub.getProcessingStatus().getArchive().equals(archive.name());
    }


    @Override
    public Map<Archive, SubmissionEnvelope> determineSupportingInformationRequired(SubmissionEnvelope submissionEnvelope) {
        //SubmissionEnvelope submissionEnvelope = submissionEnvelopeService.fetchOne(submission.getId());

        determineSupportingInformationRequiredForSamples(submissionEnvelope);

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
                ;
//                .filter(item -> archive.equals(item.getArchive()));

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

    @Override
    public void insertUploadedFiles(SubmissionEnvelope submissionEnvelope) {
        String submissionId = submissionEnvelope.getSubmission().getId();
        Map<String, File> files = filesByFilename(fileRepository.findBySubmissionId(submissionId));

        Stream<uk.ac.ebi.subs.data.component.File> filesReferencedInEnvelope = filesReferencedInEnvelope(submissionEnvelope);

        List<UploadedFile> uploadedFiles = filesReferencedInEnvelope
                .map(fileRef -> files.get(fileRef.getName()))
                .filter(Objects::nonNull)
                .map(this::convertFileToUploadedFile)
                .collect(Collectors.toList());


        submissionEnvelope.getUploadedFiles().addAll(uploadedFiles);
    }

    private Stream<uk.ac.ebi.subs.data.component.File> filesReferencedInEnvelope(SubmissionEnvelope submissionEnvelope) {
        Stream<uk.ac.ebi.subs.data.component.File> analysisFileStream = submissionEnvelope.getAnalyses()
                .stream()
                .map(Analysis::getFiles)
                .flatMap(List::stream);

        Stream<uk.ac.ebi.subs.data.component.File> assayDataFileStream = submissionEnvelope.getAssayData()
                .stream()
                .map(AssayData::getFiles)
                .flatMap(List::stream);

        return Stream.concat(
                assayDataFileStream,
                analysisFileStream
        );
    }

    private UploadedFile convertFileToUploadedFile(File file){
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setFilename(file.getFilename());
        uploadedFile.setPath(file.getTargetPath());
        uploadedFile.setSubmissionId(file.getSubmissionId());
        uploadedFile.setTotalSize(file.getTotalSize());
        uploadedFile.setChecksum(file.getChecksum());

        return uploadedFile;
    }

    public void determineSupportingInformationRequiredForSamples(SubmissionEnvelope submissionEnvelope) {
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

    private Set<String> processingStatusesToAllow;
    private Map<String, SubmittableRepository> submittableRepositoryMap;
    private Map<String, SubmittableRepository<? extends StoredSubmittable>> dataTypeRepositoryMap;
    private RefLookupService refLookupService;
    private SubmissionEnvelopeService submissionEnvelopeService;
    private ProcessingStatusBulkOperations processingStatusBulkOperations;
    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionEnvelopeStuffer submissionEnvelopeStuffer;
    private FileRepository fileRepository;

    public DispatcherServiceImpl(Map<Class<? extends StoredSubmittable>, SubmittableRepository<? extends StoredSubmittable>> submittableRepositoryMap,
                                 SubmittableConfig.DataTypeRepositoryMap dataTypeRepositoryMap,
                                 RefLookupService refLookupService, SubmissionEnvelopeService submissionEnvelopeService,
                                 ProcessingStatusBulkOperations processingStatusBulkOperations,
                                 ProcessingStatusRepository processingStatusRepository, SubmissionEnvelopeStuffer submissionEnvelopeStuffer,
                                 FileRepository fileRepository) {
        this.refLookupService = refLookupService;
        this.submissionEnvelopeService = submissionEnvelopeService;
        this.processingStatusBulkOperations = processingStatusBulkOperations;
        this.processingStatusRepository = processingStatusRepository;
        this.submissionEnvelopeStuffer = submissionEnvelopeStuffer;
        this.fileRepository = fileRepository;

        setupStatusesToProcess();
        buildSubmittableRepositoryMap(submittableRepositoryMap);
        this.dataTypeRepositoryMap = dataTypeRepositoryMap.getDataTypeRepositoryMap();
    }

    private void buildSubmittableRepositoryMap(Map<Class<? extends StoredSubmittable>, SubmittableRepository<? extends StoredSubmittable>> submittableRepositoryMap) {
        this.submittableRepositoryMap = new HashMap<>();
        submittableRepositoryMap.entrySet().forEach(es ->
                this.submittableRepositoryMap.put(es.getKey().getSimpleName(), es.getValue())
        );
    }

    private void setupStatusesToProcess() {
        processingStatusesToAllow = new HashSet<>();
        processingStatusesToAllow.add(ProcessingStatusEnum.Draft.name());
        processingStatusesToAllow.add(ProcessingStatusEnum.Submitted.name());
    }

    private Map<String, File> filesByFilename(List<File> files) {
        Map<String, File> filesByFilename = new HashMap<>();
        files.forEach(file -> filesByFilename.put(file.getFilename(), file));

        return filesByFilename;
    }
}
