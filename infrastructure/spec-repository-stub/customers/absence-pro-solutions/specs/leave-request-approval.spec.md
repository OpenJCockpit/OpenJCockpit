# leave-request-approval.spec.md

## Business intent

Employee leave requests must be processed via an automated approval flow,
in which team leads are notified in time and approval or rejection is logged.

## Acceptance criteria

- A request triggers a notification to the direct manager within 5 minutes.
- If the manager is absent, the request is escalated to HR.
- The status of the request is visible to the employee in real time.
- All status changes are stored in the audit trail.
- Unit tests cover at least 90% of the approval logic.
