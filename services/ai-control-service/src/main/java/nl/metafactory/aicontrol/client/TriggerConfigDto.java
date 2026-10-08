package nl.metafactory.aicontrol.client;

public record TriggerConfigDto(
        boolean dashboardButtonEnabled,
        boolean hermesSignalEnabled,
        String hermesSignalType,
        boolean fileDeliveryEnabled,
        String fileDeliveryFolderPath
) {
}
