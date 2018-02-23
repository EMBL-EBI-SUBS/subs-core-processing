package uk.ac.ebi.subs.sheetloader;

import uk.ac.ebi.subs.repository.repos.SheetRepository;

import java.util.Date;

public interface SheetCleanupRepository extends SheetRepository {


    void removeByLastModifiedDateBeforeAndStatus(Date lastModifiedBy, String status);
}
