package uk.ac.ebi.subs.processing.dispatcher;

import uk.ac.ebi.subs.data.Submission;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.SubmissionEnvelope;

import java.util.Map;

/**
 * Created by davidr on 27/03/2017.
 */
public interface DispatcherService {

    Map<Archive, SubmissionEnvelope> assessDispatchReadiness(Submission submission, String jwtToken);

    Map<Archive, SubmissionEnvelope> determineSupportingInformationRequired(SubmissionEnvelope submissionEnvelope);

    void updateSubmittablesStatusToSubmitted(Archive archive, SubmissionEnvelope submissionEnvelope);

    void insertReferencedSamples(SubmissionEnvelope submissionEnvelope);

    void insertUploadedFiles(SubmissionEnvelope submissionEnvelope);

}
