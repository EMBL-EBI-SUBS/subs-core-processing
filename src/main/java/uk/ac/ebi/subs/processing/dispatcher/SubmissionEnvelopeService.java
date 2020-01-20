package uk.ac.ebi.subs.processing.dispatcher;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.submittable.Project;
import uk.ac.ebi.subs.error.EntityNotFoundException;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
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
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * This is a Spring @Service component for {@link Submission} entity gathering the items of a submission
 * to an container object (envelope).
 */
@Service
public class SubmissionEnvelopeService {

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

    private List<SubmittableRepository<?>> submissionContentsRepositories;

    public SubmissionEnvelopeService(SubmissionRepository submissionRepository, AnalysisRepository analysisRepository,
                                     AssayDataRepository assayDataRepository, AssayRepository assayRepository,
                                     EgaDacPolicyRepository egaDacPolicyRepository, EgaDacRepository egaDacRepository,
                                     EgaDatasetRepository egaDatasetRepository, ProjectRepository projectRepository,
                                     ProtocolRepository protocolRepository, SampleGroupRepository sampleGroupRepository,
                                     SampleRepository sampleRepository, StudyRepository studyRepository,
                                     @Qualifier("submissionContentsRepositories") List<SubmittableRepository<?>> submissionContentsRepositories) {
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
        this.submissionContentsRepositories = submissionContentsRepositories;
    }

    public SubmissionEnvelope fetchOne(String submissionId) {
        Submission minimalSub = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new EntityNotFoundException(
                String.format("Submission entity with ID: %s is not found in the database.", submissionId)));

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

        List<uk.ac.ebi.subs.repository.model.Project> projects = projectRepository.findBySubmissionId(submissionId);
        Project project = projects.size() == 0 ? null : projects.get(0);
        submissionEnvelope.setProject(project);

        submissionEnvelope.getProtocols().addAll(protocolRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getSampleGroups().addAll(sampleGroupRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getSamples().addAll(sampleRepository.findBySubmissionId(submissionId));
        submissionEnvelope.getStudies().addAll(studyRepository.findBySubmissionId(submissionId));

        return submissionEnvelope;
    }

    public Stream<? extends StoredSubmittable> submissionContents(String submissionId) {
        Submission minimalSub = submissionRepository.findById(submissionId)
            .orElseThrow(() -> new EntityNotFoundException(
                String.format("Submission entity with ID: %s is not found in the database.", submissionId)));

        if (minimalSub == null) {
            throw new ResourceNotFoundException();
        }

        return submissionContentsRepositories.stream()
                .filter(Objects::nonNull)
                .flatMap(repo -> repo.streamBySubmissionId(submissionId));

    }
}

