package nl.metafactory.aicontrol.model;

import nl.metafactory.aicontrol.client.SkillSpecDto;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SkillCatalogRecordsTest {

    @Test
    void skillCatalogSourceKindHasExactlyLocalAndMarketplace() {
        assertThat(SkillCatalogSourceKind.values())
                .containsExactly(SkillCatalogSourceKind.LOCAL, SkillCatalogSourceKind.MARKETPLACE);
    }

    @Test
    void skillCatalogSourceOutcomeHasExactlySevenValuesInOrder() {
        assertThat(SkillCatalogSourceOutcome.values()).containsExactly(
                SkillCatalogSourceOutcome.SUCCESS,
                SkillCatalogSourceOutcome.AUTH_FAILED,
                SkillCatalogSourceOutcome.UNREACHABLE,
                SkillCatalogSourceOutcome.TIMEOUT,
                SkillCatalogSourceOutcome.INVALID_RESPONSE,
                SkillCatalogSourceOutcome.BLOCKED_BY_POLICY,
                SkillCatalogSourceOutcome.CONFIG_ERROR
        );
    }

    @Test
    void externalSkillDtoExposesExactlyFourFields() {
        var id = UUID.randomUUID();
        var dto = new ExternalSkillDto(id, "Acme Skills", "code-review", "Reviews code");

        assertThat(dto.marketplaceId()).isEqualTo(id);
        assertThat(dto.marketplaceName()).isEqualTo("Acme Skills");
        assertThat(dto.name()).isEqualTo("code-review");
        assertThat(dto.description()).isEqualTo("Reviews code");
    }

    @Test
    void externalSkillDtoAllowsNullDescription() {
        var dto = new ExternalSkillDto(UUID.randomUUID(), "Acme Skills", "code-review", null);

        assertThat(dto.description()).isNull();
    }

    @Test
    void skillCatalogSourceStatusDtoForLocalHasNullMarketplaceIdAndHttpStatus() {
        var status = new SkillCatalogSourceStatusDto(
                SkillCatalogSourceKind.LOCAL, null, "Local", SkillCatalogSourceOutcome.SUCCESS, 3, null);

        assertThat(status.kind()).isEqualTo(SkillCatalogSourceKind.LOCAL);
        assertThat(status.marketplaceId()).isNull();
        assertThat(status.name()).isEqualTo("Local");
        assertThat(status.outcome()).isEqualTo(SkillCatalogSourceOutcome.SUCCESS);
        assertThat(status.itemCount()).isEqualTo(3);
        assertThat(status.httpStatus()).isNull();
    }

    @Test
    void skillCatalogSourceStatusDtoForMarketplaceCarriesIdAndHttpStatus() {
        var id = UUID.randomUUID();
        var status = new SkillCatalogSourceStatusDto(
                SkillCatalogSourceKind.MARKETPLACE, id, "Globex Hub", SkillCatalogSourceOutcome.AUTH_FAILED, 0, 401);

        assertThat(status.kind()).isEqualTo(SkillCatalogSourceKind.MARKETPLACE);
        assertThat(status.marketplaceId()).isEqualTo(id);
        assertThat(status.name()).isEqualTo("Globex Hub");
        assertThat(status.outcome()).isEqualTo(SkillCatalogSourceOutcome.AUTH_FAILED);
        assertThat(status.itemCount()).isEqualTo(0);
        assertThat(status.httpStatus()).isEqualTo(401);
    }

    @Test
    void skillCatalogDtoCarriesAllThreeArraysAndLocalSkillsIsUnmodifiedSkillSpecDto() {
        var localSkill = new SkillSpecDto("code-review", "desc", "in", "out", "instructions", List.of(), null);
        var externalSkill = new ExternalSkillDto(UUID.randomUUID(), "Acme Skills", "code-review", "desc");
        var source = new SkillCatalogSourceStatusDto(
                SkillCatalogSourceKind.LOCAL, null, "Local", SkillCatalogSourceOutcome.SUCCESS, 1, null);

        var catalog = new SkillCatalogDto(List.of(localSkill), List.of(externalSkill), List.of(source));

        assertThat(catalog.localSkills()).containsExactly(localSkill);
        assertThat(catalog.externalSkills()).containsExactly(externalSkill);
        assertThat(catalog.sources()).containsExactly(source);
    }

    @Test
    void skillCatalogDtoArraysCanBeEmptyButNotNull() {
        var catalog = new SkillCatalogDto(List.of(), List.of(), List.of());

        assertThat(catalog.localSkills()).isEmpty();
        assertThat(catalog.externalSkills()).isEmpty();
        assertThat(catalog.sources()).isEmpty();
    }
}
