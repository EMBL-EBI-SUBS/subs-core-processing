package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.Analysis;

@Component
public class AnalysisArchiveAssignmentService implements ArchiveAssigner<Analysis> {

    private static final String SEQUENCE_VARIATION_ANALYSIS_TYPE = "sequence variation";

    @Override
    public Archive assignArchive(Analysis submittable) {
        if (SEQUENCE_VARIATION_ANALYSIS_TYPE.equals(submittable.getAnalysisType())){
            return Archive.Ena;
        }
        throw new IllegalStateException("analysis ID="+submittable.getId()+" should not have been submitted, cannot assign to an archive");
    }
}
