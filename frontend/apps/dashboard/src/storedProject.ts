const PROJECT_KEY = 'openjcockpit_project_id';

export function getStoredProjectId(): string | null {
  return localStorage.getItem(PROJECT_KEY);
}
export function setStoredProjectId(id: string): void {
  localStorage.setItem(PROJECT_KEY, id);
}
export function clearStoredProjectId(): void {
  localStorage.removeItem(PROJECT_KEY);
}
