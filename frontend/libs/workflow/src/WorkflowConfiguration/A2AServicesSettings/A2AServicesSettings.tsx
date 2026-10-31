import { useTranslation } from '@openjcockpit/i18n';
import {
  StatusChip,
  SecretField,
  TextField,
  StatusMessage,
  Button,
  Actions,
  DataTable,
  cx,
  fieldHintClass,
  formActionsClass,
  formClass,
  formGridClass,
  formTitleClass,
} from '@openjcockpit/ui';
import { useConfirmDelete } from '../../WorkflowDesign/panels/shared/useConfirmDelete';
import { a2aServiceStatus } from './a2aServiceStatus';
import { useA2AServices } from './useA2AServices';
import WorkflowConfigurationStyles from '../WorkflowConfiguration.module.scss';

/**
 * The A2A services workflows can call agents through: the ones from the agent service's
 * configuration (read-only, e.g. LiteLLM) and LiteLLM-compatible gateways added here. Global —
 * it applies to every project.
 */
export function A2AServicesSettings() {
  const { t } = useTranslation('workflow');
  const {
    services,
    loaded,
    error,
    savedMessage,
    editing,
    editingService,
    form,
    setForm,
    saving,
    startAdd,
    startEdit,
    closeForm,
    handleSave,
    handleDelete,
  } = useA2AServices();
  const { askDelete, dialog } = useConfirmDelete();

  return (
    <div className={formClass}>
      <h2 className={formTitleClass}>{t('a2aServices.title')}</h2>
      <StatusMessage>{t('a2aServices.intro')}</StatusMessage>
      {error && (
        <StatusMessage variant="error" role="alert">
          {error}
        </StatusMessage>
      )}
      {savedMessage && (
        <p className={WorkflowConfigurationStyles.workflowSavedMessage}>{savedMessage}</p>
      )}

      {!loaded ? (
        <StatusMessage role="status">{t('a2aServices.loading')}</StatusMessage>
      ) : (
        <div className={WorkflowConfigurationStyles.servicesTableWrap}>
          <DataTable>
            <thead>
              <tr>
                <th>{t('a2aServices.columns.service')}</th>
                <th>{t('a2aServices.columns.baseUrl')}</th>
                <th>{t('a2aServices.columns.credentials')}</th>
                <th>{t('a2aServices.columns.status')}</th>
                <th>{t('a2aServices.columns.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {services.map((service) => {
                const status = a2aServiceStatus(service);
                return (
                  <tr key={service.id}>
                    <td>
                      <strong>{service.name}</strong>
                      <span className={fieldHintClass}>
                        {service.id} ·{' '}
                        {service.source === 'CONFIGURATION'
                          ? t('a2aServices.source.CONFIGURATION')
                          : t('a2aServices.source.ADDED')}
                      </span>
                    </td>
                    <td className={WorkflowConfigurationStyles.servicesUrl}>
                      {service.baseUrl}
                      {service.publicUrl && service.publicUrl !== service.baseUrl && (
                        <span className={fieldHintClass}>
                          {t('a2aServices.publicUrl', { url: service.publicUrl })}
                        </span>
                      )}
                    </td>
                    <td>
                      {service.hasApiKey ? t('a2aServices.apiKeySet') : t('a2aServices.noApiKey')}
                      <span className={fieldHintClass}>
                        {service.callerClientId
                          ? t('a2aServices.callerIdentity', { id: service.callerClientId })
                          : t('a2aServices.noCallerIdentity')}
                      </span>
                    </td>
                    <td>
                      <StatusChip label={status.label} kind={status.kind} />
                      {service.message && <span className={fieldHintClass}>{service.message}</span>}
                    </td>
                    <td>
                      {service.editable ? (
                        <Actions>
                          <Button variant="secondary" onClick={() => startEdit(service)}>
                            {t('shared.edit')}
                          </Button>
                          <Button
                            variant="danger"
                            onClick={() =>
                              askDelete('a2aService', service.name, () => handleDelete(service))
                            }
                          >
                            {t('shared.delete')}
                          </Button>
                        </Actions>
                      ) : (
                        <span className={fieldHintClass}>{t('shared.readOnly')}</span>
                      )}
                    </td>
                  </tr>
                );
              })}
              {services.length === 0 && !error && (
                <tr>
                  <td colSpan={5} className={fieldHintClass}>
                    {t('a2aServices.empty')}
                  </td>
                </tr>
              )}
            </tbody>
          </DataTable>
        </div>
      )}

      {editing ? (
        <div className={WorkflowConfigurationStyles.servicesForm}>
          <h3 className={WorkflowConfigurationStyles.servicesFormTitle}>
            {editingService
              ? t('a2aServices.formTitleEdit', { name: editingService.name })
              : t('a2aServices.formTitleAdd')}
          </h3>
          <p className={fieldHintClass}>{t('a2aServices.formHint')}</p>
          <div className={cx(formGridClass, WorkflowConfigurationStyles.servicesFormGrid)}>
            <TextField
              label={t('a2aServices.fields.id')}
              required
              value={form.id}
              onChange={(v) => setForm((f) => ({ ...f, id: v }))}
              placeholder={t('a2aServices.fields.idPlaceholder')}
              disabled={editingService !== null}
              hint={
                editingService
                  ? t('a2aServices.fields.idHintEdit')
                  : t('a2aServices.fields.idHintAdd')
              }
            />
            <TextField
              label={t('a2aServices.fields.name')}
              required
              value={form.name}
              onChange={(v) => setForm((f) => ({ ...f, name: v }))}
              placeholder={t('a2aServices.fields.namePlaceholder')}
            />
            <TextField
              label={t('a2aServices.fields.baseUrl')}
              required
              value={form.baseUrl}
              onChange={(v) => setForm((f) => ({ ...f, baseUrl: v }))}
              placeholder={t('a2aServices.fields.baseUrlPlaceholder')}
              hint={t('a2aServices.fields.baseUrlHint')}
            />
            <TextField
              label={t('a2aServices.fields.publicUrl')}
              value={form.publicUrl}
              onChange={(v) => setForm((f) => ({ ...f, publicUrl: v }))}
              placeholder={t('a2aServices.fields.publicUrlPlaceholder')}
              hint={t('a2aServices.fields.publicUrlHint')}
            />
            <SecretField
              label={
                editingService
                  ? t('a2aServices.fields.apiKeyEdit')
                  : t('a2aServices.fields.apiKeyAdd')
              }
              hasValue={editingService?.hasApiKey ?? false}
              required={!editingService}
              value={form.apiKey}
              onChange={(v) => setForm((f) => ({ ...f, apiKey: v }))}
            />
            <TextField
              label={t('a2aServices.fields.callerTokenUrl')}
              value={form.callerTokenUrl}
              onChange={(v) => setForm((f) => ({ ...f, callerTokenUrl: v }))}
              placeholder={t('a2aServices.fields.callerTokenUrlPlaceholder')}
              hint={t('a2aServices.fields.callerTokenUrlHint')}
            />
            <TextField
              label={t('a2aServices.fields.callerClientId')}
              value={form.callerClientId}
              onChange={(v) => setForm((f) => ({ ...f, callerClientId: v }))}
              placeholder={t('a2aServices.fields.callerClientIdPlaceholder')}
            />
            <SecretField
              label={t('a2aServices.fields.callerClientSecret')}
              hasValue={editingService?.hasCallerClientSecret ?? false}
              value={form.callerClientSecret}
              onChange={(v) => setForm((f) => ({ ...f, callerClientSecret: v }))}
            />
          </div>
          <div className={formActionsClass}>
            <Button variant="primary" onClick={() => void handleSave(editing)} disabled={saving}>
              {saving
                ? t('shared.saving')
                : editingService
                  ? t('a2aServices.saveService')
                  : t('a2aServices.addService')}
            </Button>
            <Button variant="secondary" onClick={closeForm} disabled={saving}>
              {t('shared.cancel')}
            </Button>
          </div>
        </div>
      ) : (
        loaded && (
          <Button variant="primary" onClick={startAdd}>
            {t('a2aServices.addButton')}
          </Button>
        )
      )}
      {dialog}
    </div>
  );
}
