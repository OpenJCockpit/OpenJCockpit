package nl.metafactory.aicontrol.service;

import nl.metafactory.aicontrol.model.SpecFile;
import java.util.List;

public interface SpecRepositoryClient {
    List<SpecFile> listSpecs(String customerId);
}
