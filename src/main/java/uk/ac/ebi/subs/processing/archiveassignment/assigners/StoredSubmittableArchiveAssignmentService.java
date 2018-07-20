package uk.ac.ebi.subs.processing.archiveassignment.assigners;

import org.springframework.stereotype.Component;
import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.processing.archiveassignment.ArchiveAssigner;
import uk.ac.ebi.subs.repository.model.*;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

@Component
public class StoredSubmittableArchiveAssignmentService implements ArchiveAssigner<StoredSubmittable> {

    private Map<Class<? extends StoredSubmittable>, ArchiveAssigner<? extends StoredSubmittable>> archiveAssignmentServiceMap;

    public StoredSubmittableArchiveAssignmentService(
            AssayArchiveAssignmentService assayArchiveAssignmentService,
            AssayDataArchiveAssignmentService assayDataArchiveAssignmentService,
            ProjectArchiveAssignmentService projectArchiveAssignmentService,
            SampleArchiveAssignmentService sampleArchiveAssignmentService,
            StudyArchiveAssignmentService studyArchiveAssignmentService,
            EgaDatasetArchiveAssignmentService egaDatasetArchiveAssignmentService,
            EgaDacPolicyArchiveAssignmentService egaDacPolicyArchiveAssignmentService,
            EgaDacArchiveAssignmentService egaDacArchiveAssignmentService,
            AnalysisArchiveAssignmentService analysisArchiveAssignmentService) {
        archiveAssignmentServiceMap = new HashMap<>();

        archiveAssignmentServiceMap.put(Assay.class, assayArchiveAssignmentService);
        archiveAssignmentServiceMap.put(AssayData.class, assayDataArchiveAssignmentService);
        archiveAssignmentServiceMap.put(Project.class, projectArchiveAssignmentService);
        archiveAssignmentServiceMap.put(Sample.class, sampleArchiveAssignmentService);
        archiveAssignmentServiceMap.put(Study.class, studyArchiveAssignmentService);
        archiveAssignmentServiceMap.put(EgaDac.class, egaDacArchiveAssignmentService);
        archiveAssignmentServiceMap.put(EgaDacPolicy.class, egaDacPolicyArchiveAssignmentService);
        archiveAssignmentServiceMap.put(EgaDataset.class, egaDatasetArchiveAssignmentService);
        archiveAssignmentServiceMap.put(Analysis.class, analysisArchiveAssignmentService);
    }




    @Override
    public Archive assignArchive(StoredSubmittable storedSubmittable) {
        Class clazz = ((Object) storedSubmittable).getClass();
        ArchiveAssigner archiveAssignmentService = archiveAssignmentServiceMap.get(clazz);

        if (archiveAssignmentService == null) {
            String message = MessageFormat.format("Need archiveAssignmentService for class {} in submission {} ",
                    archiveAssignmentService, clazz);

            throw new IllegalStateException(message);
        }

        Archive archive = archiveAssignmentService.assignArchive(storedSubmittable);

        return archive;
    }
}
