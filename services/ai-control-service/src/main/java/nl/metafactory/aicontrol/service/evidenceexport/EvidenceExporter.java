package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;

import java.io.IOException;
import java.io.OutputStream;

/** Renders an export model to one file format. Implementations never close the stream. */
public interface EvidenceExporter {
    void write(EvidenceExportModel model, OutputStream out) throws IOException;
}
