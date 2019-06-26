package uk.ac.ebi.subs.processing.utils;

import uk.ac.ebi.subs.data.component.Archive;
import uk.ac.ebi.subs.repository.model.DataType;
import uk.ac.ebi.subs.repository.repos.DataTypeRepository;

public class DataTypeBuilder  {

    public static DataType buildDataType(Archive archive, DataTypeRepository dataTypeRepository, String dataTypeId) {
        DataType dt = new DataType();
        dt.setArchive(archive);
        dt.setId(dataTypeId);
        dataTypeRepository.insert(dt);

        return dt;
    }
}
