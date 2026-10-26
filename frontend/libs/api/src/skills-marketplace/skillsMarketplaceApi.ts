import { apiRequest, apiRequestVoid } from '../core/http';
import type {
  SkillsMarketplaceConnection,
  SkillsMarketplaceConnectionRequest,
} from '../types/types';

// listSkillsMarketplaces intentionally throws on failure (unlike loadProjects' swallow-to-[]
// idiom) — an empty array must mean "genuinely zero marketplaces", never "request failed",
// so the Skills Hub Settings empty state is never shown for a transport or auth error.
export function listSkillsMarketplaces(): Promise<SkillsMarketplaceConnection[]> {
  return apiRequest('/api/skills-marketplaces', { errorMessage: 'Failed to load marketplaces' });
}

export function createSkillsMarketplace(
  req: SkillsMarketplaceConnectionRequest,
): Promise<SkillsMarketplaceConnection> {
  return apiRequest('/api/skills-marketplaces', {
    method: 'POST',
    body: req,
    errorMessage: 'Failed to create marketplace',
  });
}

export function updateSkillsMarketplace(
  id: string,
  req: SkillsMarketplaceConnectionRequest,
): Promise<SkillsMarketplaceConnection> {
  return apiRequest(`/api/skills-marketplaces/${id}`, {
    method: 'PUT',
    body: req,
    errorMessage: 'Failed to update marketplace',
  });
}

export function deleteSkillsMarketplace(id: string): Promise<void> {
  return apiRequestVoid(`/api/skills-marketplaces/${id}`, {
    method: 'DELETE',
    errorMessage: 'Failed to delete marketplace',
  });
}
