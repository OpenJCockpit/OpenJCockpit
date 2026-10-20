export const DEFAULT_FOLDER_BASE_PATH = '/documents';

// Folders can't contain spaces, so the folder name is always derived as a space-free
// CamelCase (PascalCase) version of the project name, e.g. "Absence Pro Solutions" -> "AbsenceProSolutions".
export function toCamelCaseFolderName(input: string): string {
  return input
    .split(/[^a-zA-Z0-9]+/)
    .filter(Boolean)
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join('');
}

export function basePathOf(fullPath: string): string {
  const trimmed = fullPath.replace(/\/+$/, '');
  const lastSlash = trimmed.lastIndexOf('/');
  return lastSlash > 0 ? trimmed.slice(0, lastSlash) : '';
}

// The folder name is always appended as the trailing segment of the location — never a
// separately editable part of the path — so the base and name are joined here rather than
// letting users type the full path directly.
export function joinFolderPath(basePath: string, folderName: string): string {
  const trimmedBase = basePath.replace(/\/+$/, '');
  return trimmedBase ? `${trimmedBase}/${folderName}` : `/${folderName}`;
}
