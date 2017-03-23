package uk.ac.ebi.subs.apisupport;


import org.springframework.stereotype.Service;
import uk.ac.ebi.subs.data.status.ProcessingStatusEnum;
import uk.ac.ebi.subs.data.status.SubmissionStatusEnum;
import uk.ac.ebi.subs.repository.model.ProcessingStatus;
import uk.ac.ebi.subs.repository.model.StoredSubmittable;
import uk.ac.ebi.subs.repository.model.Submission;
import uk.ac.ebi.subs.repository.model.SubmissionStatus;
import uk.ac.ebi.subs.repository.repos.SubmissionRepository;
import uk.ac.ebi.subs.repository.repos.status.ProcessingStatusRepository;
import uk.ac.ebi.subs.repository.repos.status.SubmissionStatusRepository;
import uk.ac.ebi.subs.repository.repos.submittables.SubmittableRepository;

import java.util.List;

@Service
public class ApiSupportServiceImpl implements ApiSupportService {

    private List<SubmittableRepository<?>> submissionContentsRepositories;
    private ProcessingStatusRepository processingStatusRepository;
    private SubmissionStatusRepository submissionStatusRepository;
    private SubmissionRepository submissionRepository;

    public ApiSupportServiceImpl(List<SubmittableRepository<?>> submissionContentsRepositories, ProcessingStatusRepository processingStatusRepository, SubmissionStatusRepository submissionStatusRepository, SubmissionRepository submissionRepository) {
        this.submissionContentsRepositories = submissionContentsRepositories;
        this.processingStatusRepository = processingStatusRepository;
        this.submissionStatusRepository = submissionStatusRepository;
        this.submissionRepository = submissionRepository;
    }

    @Override
    public void deleteSubmissionContents(Submission submission) {

        if (submissionRepository.findOne(submission.getId()) != null) {
            return; // submission was not actually deleted
        }

        processingStatusRepository.deleteBySubmissionId(submission.getId());
        submissionStatusRepository.delete(submission.getSubmissionStatus());

        submissionContentsRepositories.stream().forEach(repo -> repo.deleteBySubmissionId(submission.getId()));
    }

    @Override
    public void markContentsAsSubmitted(Submission submission) {

        Submission currentSubmissionState = submissionRepository.findOne(submission.getId());
        if (SubmissionStatusEnum.Draft.name().equals(currentSubmissionState.getSubmissionStatus().getStatus())){
            return; //status update did not succeed, return
        }

        submissionContentsRepositories
                .stream()
                .flatMap(repo -> repo.streamBySubmissionId(submission.getId()))
                .filter(item -> ProcessingStatusEnum.Draft.name().equals(item.getProcessingStatus().getStatus()))
                .map(item -> {
                    ProcessingStatus status = item.getProcessingStatus();
                    status.copyDetailsFromSubmittable(item);
                    status.setStatus(ProcessingStatusEnum.Submitted);
                    return status;
                })
                .forEach(processingStatus -> processingStatusRepository.save(processingStatus))
        ;



    }
}
