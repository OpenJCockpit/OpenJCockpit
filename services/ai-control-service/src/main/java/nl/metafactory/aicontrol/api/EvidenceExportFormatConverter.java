package nl.metafactory.aicontrol.api;

import nl.metafactory.aicontrol.model.EvidenceExportFormat;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/** Binds the lowercase {@code format} query value ({@code json}, {@code csv}, ...); unknown values become a 400. */
@Component
public class EvidenceExportFormatConverter implements Converter<String, EvidenceExportFormat> {

    @Override
    public EvidenceExportFormat convert(String source) {
        return EvidenceExportFormat.fromValue(source)
                .orElseThrow(() -> new IllegalArgumentException("Unknown export format: " + source));
    }
}
