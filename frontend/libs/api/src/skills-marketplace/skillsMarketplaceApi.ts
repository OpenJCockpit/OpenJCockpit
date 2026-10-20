import { errorMessageFrom } from '../core/httpErrors';
import { API_BASE_URL, authHeaders } from '../core/http';
import type {
  SkillsMarketplaceConnection,
  SkillsMarketplaceConnectionRequest,
} from '../types/types';

// listSkillsMarketplaces intentionally throws on failure (unlike loadProjects' swallow-to-[]
// idiom) — an empty array must mean "genuinely zero marketplaces", never "request failed",
// so the Skills Hub Settings empty state is never shown for a transport or auth error.
export async function listSkillsMarketplaces(): Promise<SkillsMarketplaceConnection[]> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces`, {
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to load marketplaces'));
  }
  return response.json();
}

export async function createSkillsMarketplace(
  req: SkillsMarketplaceConnectionRequest,
): Promise<SkillsMarketplaceConnection> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to create marketplace'));
  }
  return response.json();
}

export async function updateSkillsMarketplace(
  id: string,
  req: SkillsMarketplaceConnectionRequest,
): Promise<SkillsMarketplaceConnection> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces/${id}`, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to update marketplace'));
  }
  return response.json();
}

export async function deleteSkillsMarketplace(id: string): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/skills-marketplaces/${id}`, {
    method: 'DELETE',
    headers: authHeaders(),
  });
  if (!response.ok) {
    throw new Error(await errorMessageFrom(response, 'Failed to delete marketplace'));
  }
}
