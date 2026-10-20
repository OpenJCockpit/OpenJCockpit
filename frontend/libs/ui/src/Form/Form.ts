import styles from './Form.module.scss';

/**
 * Shared form styling. Exposed as class names rather than components because the same
 * look is applied to many element types (p, span, td, div, input, select, textarea).
 */
export const formClass = styles.form;
export const formTitleClass = styles.formTitle;
export const formGridClass = styles.formGrid;
export const formInputClass = styles.input;
/** Muted helper text under a field, or a muted status line. */
export const fieldHintClass = styles.fieldHint;
/** Red inline error line. */
export const fieldErrorClass = styles.fieldError;
/** Save / Cancel button row at the bottom of a form. */
export const formActionsClass = styles.formActions;
