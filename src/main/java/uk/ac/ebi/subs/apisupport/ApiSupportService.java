package uk.ac.ebi.subs.apisupport;


import uk.ac.ebi.subs.repository.model.Submission;

public interface ApiSupportService {

    /**
     * After a submission has been deleted through the API, cleanup its lingering contents
     *
     * @param submission
     */
    void deleteSubmissionContents(Submission submission);
}
