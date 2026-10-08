package nl.metafactory.agents.workflow.model;

/**
 * Describes how a workflow may be started. Only the dashboard-button trigger is actually wired up today;
 * the Hermes-signal and file-delivery fields are configuration placeholders for future listeners
 * (see nl.metafactory.aicontrol.workflow.FolderTriggerWatcher in ai-control-service).
 */
public record TriggerConfig(
        boolean dashboardButtonEnabled,
        boolean hermesSignalEnabled,
        String hermesSignalType,
        boolean fileDeliveryEnabled,
        String fileDeliveryFolderPath
) {
}
