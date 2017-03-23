package uk.ac.ebi.subs.apisupport;


import uk.ac.ebi.subs.repository.model.Submission;

public interface ApiSupportService {

    /**
     * After a submission has been deleted through the API, cleanup its lingering contents
     *
     * @param submission
     */
    void deleteSubmissionContents(Submission submission);

    /**
     * Once a submission has been submitted, change the processing status of its submittables from 'draft' to 'submitted'
     *
     * @param submission
     */
    void markContentsAsSubmitted(Submission submission);
}
