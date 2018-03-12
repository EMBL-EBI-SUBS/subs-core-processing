package uk.ac.ebi.subs.processing.dispatcher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.processing.fileupload.UploadedFile;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.fileupload.FileRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AnalysisRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayDataRepository;
import uk.ac.ebi.subs.repository.repos.submittables.AssayRepository;
import uk.ac.ebi.subs.repository.repos.submittables.EgaDacPolicyRepository;
import uk.ac.ebi.subs.repository.repos.submittables.EgaDacRepository;
import uk.ac.ebi.subs.repository.repos.submittables.EgaDatasetRepository;
import uk.ac.ebi.subs.repository.repos.submittables.ProjectRepository;
import uk.ac.ebi.subs.repository.repos.submittables.ProtocolRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleGroupRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SampleRepository;
import uk.ac.ebi.subs.repository.repos.submittables.StudyRepository;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class SubmissionEnvelopeService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private SubmissionRepository submissionRepository;
    private AnalysisRepository analysisRepository;
    private AssayDataRepository assayDataRepository;
    private AssayRepository assayRepository;
    private EgaDacPolicyRepository egaDacPolicyRepository;
    private EgaDacRepository egaDacRepository;
    private EgaDatasetRepository egaDatasetRepository;
    private ProjectRepository projectRepository;
    private ProtocolRepository protocolRepository;
    private SampleGroupRepository sampleGroupRepository;
    private SampleRepository sampleRepository;
    private StudyRepository studyRepository;
    private FileRepository fileRepository;

    public SubmissionEnvelopeService(SubmissionRepository submissionRepository, AnalysisRepository analysisRepository,
                                     AssayDataRepository assayDataRepository, AssayRepository assayRepository,
                                     EgaDacPolicyRepository egaDacPolicyRepository, EgaDacRepository egaDacRepository,
                                     EgaDatasetRepository egaDatasetRepository, ProjectRepository projectRepository,
                                     ProtocolRepository protocolRepository, SampleGroupRepository sampleGroupRepository,
                                     SampleRepository sampleRepository, StudyRepository studyRepository,
                                     FileRepository fileRepository) {
        this.submissionRepository = submissionRepository;
        this.analysisRepository = analysisRepository;
        this.assayDataRepository = assayDataRepository;
        this.assayRepository = assayRepository;
        this.egaDacPolicyRepository = egaDacPolicyRepository;
        this.egaDacRepository = egaDacRepository;
        this.egaDatasetRepository = egaDatasetRepository;
        this.projectRepository = projectRepository;
        this.protocolRepository = protocolRepository;
        this.sampleGroupRepository = sampleGroupRepository;
        this.sampleRepository = sampleRepository;
        this.studyRepository = studyRepository;
        this.fileRepository = fileRepository;
    }

    public SubmissionEnvelope fetchOne(String submissionId) {
        Submission minimalSub = submissionRepository.findOne(submissionId);

        if (minimalSub == null) {
            throw new ResourceNotFoundException();
        }

        SubmissionEnvelope submissionEnvelope = new SubmissionEnvelope(minimalSub);

        submissionEnvelope.getAnalyses().addAll(analysisRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getAssayData().addAll(assayDataRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getAssays().addAll(assayRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getEgaDacPolicies().addAll(egaDacPolicyRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getEgaDacs().addAll(egaDacRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getEgaDatasets().addAll(egaDatasetRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getProjects().addAll(projectRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getProtocols().addAll(protocolRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getSampleGroups().addAll(sampleGroupRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getSamples().addAll(sampleRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getStudies().addAll(studyRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getUploadedFiles().addAll(
                fileRepository.findBySubmissionId(submissionId).stream().map( file -> {
                    UploadedFile uploadedFile = new UploadedFile();
                    uploadedFile.setFilename(file.getFilename());
                    uploadedFile.setPath(file.getTargetPath());
                    uploadedFile.setSubmissionId(file.getSubmissionId());
                    uploadedFile.setTotalSize(file.getTotalSize());
                    uploadedFile.setChecksum(file.getChecksum());

                    return uploadedFile;
                }
                ).collect(Collectors.toList())
        );

        return submissionEnvelope;
    }

    public Stream<? extends StoredSubmittable> submissionContents(String submissionId) {
        Submission minimalSub = submissionRepository.findOne(submissionId);

        if (minimalSub == null) {
            throw new ResourceNotFoundException();
        }

        return Stream.of(
                analysisRepository,
                assayDataRepository,
                assayRepository,
                egaDacPolicyRepository,
                egaDacRepository,
                egaDatasetRepository,
                projectRepository,
                protocolRepository,
                sampleGroupRepository,
                sampleRepository,
                studyRepository
        )
                .filter(r -> r != null)
                .flatMap(repo -> repo.streamBySubmissionId(submissionId))
                ;
    }
}
