package nl.metafactory.aicontrol.service.evidenceexport;

import nl.metafactory.aicontrol.model.EvidenceExportModel;
import nl.metafactory.aicontrol.model.EvidenceExportScope;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.ObjectWriter;
import tools.jackson.databind.json.JsonMapper;

import java.io.OutputStream;

/**
 * Full-fidelity JSON. A run export is one {@code EvidenceExportRun} object, workflow and project
 * exports are the whole model ({@code scope}, {@code exportedAt}, {@code runs}).
 */
public class EvidenceJsonExporter implements EvidenceExporter {

    private final ObjectWriter writer;

    public EvidenceJsonExporter(JsonMapper mapper) {
        this.writer = mapper.writerWithDefaultPrettyPrinter().without(StreamWriteFeature.AUTO_CLOSE_TARGET);
    }

    @Override
    public void write(EvidenceExportModel model, OutputStream out) {
        if (model.scope() == EvidenceExportScope.RUN && model.runs().size() == 1) {
            writer.writeValue(out, model.runs().get(0));
        } else {
            writer.writeValue(out, model);
        }
    }
}
