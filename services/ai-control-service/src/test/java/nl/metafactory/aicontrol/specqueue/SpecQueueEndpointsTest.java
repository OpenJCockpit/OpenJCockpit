package nl.metafactory.aicontrol.specqueue;

import nl.metafactory.aicontrol.client.EmbabelAgentClient;
import nl.metafactory.aicontrol.client.WorkflowDefinitionDto;
import nl.metafactory.aicontrol.model.Project;
import nl.metafactory.aicontrol.model.SpecFile;
import nl.metafactory.aicontrol.model.SpecQueueFailureReason;
import nl.metafactory.aicontrol.model.SpecQueueItemStatus;
import nl.metafactory.aicontrol.repository.ProjectRepository;
import nl.metafactory.aicontrol.service.ProjectSpecService;
import nl.metafactory.aicontrol.specqueue.domain.SpecQueueItem;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueEventRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueItemRepository;
import nl.metafactory.aicontrol.specqueue.persistence.SpecQueueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SpecQueueEndpointsTest {

    @Autowired MockMvc mvc;
    @Autowired ProjectRepository projects;
    @Autowired SpecQueueRepository queues;
    @Autowired SpecQueueItemRepository items;
    @Autowired SpecQueueEventRepository events;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @MockitoBean JwtDecoder jwtDecoder;
    @MockitoBean EmbabelAgentClient embabel;
    @MockitoBean ProjectSpecService specService;

    UUID projectId;

    @BeforeEach
    void setUp() {
        // The event repository is append-only by design, so clean up with plain SQL, children first.
        for (String table : List.of("spec_queue_events", "spec_queue_item_pull_requests", "spec_queue_items", "spec_queues")) {
            jdbc.update("delete from " + table);
        }
        Project p = new Project();
        p.setName("proj-" + UUID.randomUUID());
        projectId = projects.save(p).getId();
        when(embabel.getWorkflowStrict("wf-1")).thenReturn(Optional.of(workflow("wf-1", p.getName(), false)));
        when(specService.listSpecFiles(projectId)).thenReturn(
                List.of(specFile("a.md", "héllo"), specFile("b.md", "b"), specFile("sub/c.md", "c")));
    }

    private static WorkflowDefinitionDto workflow(String id, String project, boolean prompt) {
        return new WorkflowDefinitionDto(id, "Workflow " + id, project, null, null, List.of(), List.of(), List.of(),
                List.of(), null, null, prompt, null, null, null, null, null, null);
    }

    private static SpecFile specFile(String name, String content) {
        return new SpecFile(name, name, null, null, null, false, content, null);
    }

    private static RequestPostProcessor user() {
        return jwt().jwt(j -> j.subject("user-1").claim("preferred_username", "alice"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.subject("admin-1").claim("realm_access", Map.of("roles", List.of("openjcockpit-admin"))))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_openjcockpit-admin"));
    }

    private String base() {
        return "/api/projects/" + projectId + "/spec-queue";
    }

    private String enqueue(String file) throws Exception {
        String body = mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"" + file + "\",\"workflowId\":\"wf-1\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return body.replaceAll(".*\"id\":\"([^\"]+)\".*", "$1");
    }

    @Test
    void emptyQueueReadsAsDefault() throws Exception {
        mvc.perform(get(base()).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"))
                .andExpect(jsonPath("$.autoMergeAllowed").value(false))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(base())).andExpect(status().isUnauthorized());
    }

    @Test
    void unknownProjectIs404() throws Exception {
        mvc.perform(get("/api/projects/" + UUID.randomUUID() + "/spec-queue").with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
    }

    @Test
    void enqueueAppendsWithRankAndStoresUtf8Size() throws Exception {
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"wf-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.createdBy").value("alice"))
                .andExpect(jsonPath("$.workflowName").value("Workflow wf-1"));
        enqueue("sub/c.md");

        List<SpecQueueItem> all = items.findByProjectIdOrderByPositionAsc(projectId);
        assertThat(all).extracting(SpecQueueItem::getPosition).containsExactly(1L, 2L);
        assertThat(all.get(0).getSpecFileSizeBytes()).isEqualTo(6);
        assertThat(events.findByItemIdOrderByCreatedAtAsc(all.get(0).getId())).hasSize(1);
        assertThat(events.findByItemIdOrderByCreatedAtAsc(all.get(0).getId()).get(0).getActor()).isEqualTo("USER:user-1");
    }

    @Test
    void enqueueRejectsDuplicates() throws Exception {
        enqueue("a.md");
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"wf-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_SPEC_FILE"));
    }

    @Test
    void enqueueRejectsUnsafePathsWithoutRemoteCalls() throws Exception {
        for (String path : List.of("../a.md", "/etc/passwd", "a//b.md", "a\\\\b.md", "./a.md")) {
            mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"specFile\":\"" + path + "\",\"workflowId\":\"wf-1\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        verify(specService, never()).listSpecFiles(any());
        verify(embabel, never()).getWorkflowStrict(any());
    }

    @Test
    void enqueueRejectsUnlistedFileUnknownWorkflowAndPromptWorkflow() throws Exception {
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"nope.md\",\"workflowId\":\"wf-1\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPEC_FILE_NOT_FOUND"));
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"missing\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WORKFLOW_NOT_FOUND"));
        when(embabel.getWorkflowStrict("wf-p")).thenReturn(Optional.of(workflow("wf-p", null, true)));
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"wf-p\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKFLOW_PROMPT_REQUIRED"));
        when(embabel.getWorkflowStrict("wf-o")).thenReturn(Optional.of(workflow("wf-o", "other", false)));
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"wf-o\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKFLOW_NOT_ALLOWED_FOR_PROJECT"));
    }

    @Test
    void invalidBodyAndPathAre400() throws Exception {
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON).content("nope"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/not-a-uuid/spec-queue").with(user()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void autoMergeNeedsProjectPermission() throws Exception {
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"wf-1\",\"autoMerge\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTO_MERGE_NOT_ALLOWED"));

        mvc.perform(put(base() + "/settings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoMergeAllowed\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoMergeAllowed").value(true));
        mvc.perform(post(base() + "/items").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFile\":\"a.md\",\"workflowId\":\"wf-1\",\"autoMerge\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.autoMerge").value(true));
    }

    @Test
    void settingsRequireAdminRoleToChange() throws Exception {
        mvc.perform(get(base() + "/settings").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.autoMergeAllowed").value(false));
        mvc.perform(put(base() + "/settings").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoMergeAllowed\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(base() + "/settings").with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(base() + "/settings").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"autoMergeAllowed\":true}"))
                .andExpect(status().isOk());
        var recorded = events.findByProjectIdOrderByCreatedAtDesc(projectId, org.springframework.data.domain.PageRequest.of(0, 5));
        assertThat(recorded).hasSize(1);
        assertThat(recorded.get(0).getActor()).isEqualTo("USER:admin-1");
        assertThat(recorded.get(0).getReasonCode()).isEqualTo("AUTO_MERGE_ALLOWED_ON");
    }

    @Test
    void updateChangesQueuedItemOnly() throws Exception {
        String id = enqueue("a.md");
        when(embabel.getWorkflowStrict("wf-2")).thenReturn(Optional.of(workflow("wf-2", null, false)));
        mvc.perform(patch(base() + "/items/" + id).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowId\":\"wf-2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("wf-2"));
        mvc.perform(patch(base() + "/items/" + id).with(user()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        SpecQueueItem item = items.findById(UUID.fromString(id)).orElseThrow();
        item.transitionTo(SpecQueueItemStatus.FAILED, Instant.now());
        items.saveAndFlush(item);
        mvc.perform(patch(base() + "/items/" + id).with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workflowId\":\"wf-1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_EDITABLE"));
    }

    @Test
    void reorderRequiresExactPermutation() throws Exception {
        String a = enqueue("a.md");
        String b = enqueue("b.md");
        String c = enqueue("sub/c.md");
        mvc.perform(put(base() + "/order").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[\"" + c + "\",\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(c))
                .andExpect(jsonPath("$.items[0].position").value(1))
                .andExpect(jsonPath("$.items[2].id").value(b));
        assertThat(items.findByProjectIdOrderByPositionAsc(projectId)).extracting(SpecQueueItem::getPosition)
                .containsExactly(1L, 2L, 3L);

        mvc.perform(put(base() + "/order").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_ORDER"));
        mvc.perform(put(base() + "/order").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"itemIds\":[\"" + a + "\",\"" + a + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void removeQueuedItemFreesTheSpecFileForRequeueing() throws Exception {
        String id = enqueue("a.md");
        mvc.perform(delete(base() + "/items/" + id).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REMOVED"));
        mvc.perform(delete(base() + "/items/" + id).with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_REMOVABLE"));
        enqueue("a.md");
        mvc.perform(get(base()).with(user()))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void itemsOfOtherProjectsAreNotFound() throws Exception {
        String id = enqueue("a.md");
        Project other = new Project();
        other.setName("other-" + UUID.randomUUID());
        UUID otherId = projects.save(other).getId();
        mvc.perform(delete("/api/projects/" + otherId + "/spec-queue/items/" + id).with(user()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FOUND"));
        assertThat(items.findById(UUID.fromString(id)).orElseThrow().getStatus()).isEqualTo(SpecQueueItemStatus.QUEUED);
    }

    @Test
    void failedItemBlocksResumeUntilRetriedAndRetryGoesToFront() throws Exception {
        String a = enqueue("a.md");
        String b = enqueue("b.md");
        SpecQueueItem failed = items.findById(UUID.fromString(a)).orElseThrow();
        failed.transitionTo(SpecQueueItemStatus.FAILED, Instant.now());
        failed.recordFailureReason(SpecQueueFailureReason.RUN_FAILED, Instant.now());
        items.saveAndFlush(failed);

        mvc.perform(post(base() + "/pause").with(user())).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("PAUSED"));
        mvc.perform(post(base() + "/resume").with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNRESOLVED_FAILED_ITEM"));
        mvc.perform(post(base() + "/items/" + b + "/retry").with(user()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ITEM_NOT_FAILED"));
        mvc.perform(post(base() + "/items/" + a + "/retry").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.position").value(1))
                .andExpect(jsonPath("$.failureReason").doesNotExist());
        mvc.perform(post(base() + "/resume").with(user())).andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("ACTIVE"));
    }

    @Test
    void skipFinishesFailedItem() throws Exception {
        String a = enqueue("a.md");
        SpecQueueItem failed = items.findById(UUID.fromString(a)).orElseThrow();
        failed.transitionTo(SpecQueueItemStatus.FAILED, Instant.now());
        items.saveAndFlush(failed);
        mvc.perform(post(base() + "/items/" + a + "/skip").with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SKIPPED"));
        mvc.perform(get(base()).with(user()))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.recentlyFinished[0].status").value("SKIPPED"));
    }

    @Test
    void cancellingRunningItemStopsRunThenCancelsAndPauses() throws Exception {
        String a = enqueue("a.md");
        SpecQueueItem running = items.findById(UUID.fromString(a)).orElseThrow();
        running.transitionTo(SpecQueueItemStatus.RUNNING, Instant.now());
        running.recordRun("run-1", Instant.now());
        items.saveAndFlush(running);

        mvc.perform(delete(base() + "/items/" + a).with(user()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        verify(embabel).stopAgentRun("run-1");
        mvc.perform(get(base()).with(user())).andExpect(jsonPath("$.state").value("PAUSED"));
    }

    @Test
    void failedStopLeavesRunningItemUntouched() throws Exception {
        String a = enqueue("a.md");
        SpecQueueItem running = items.findById(UUID.fromString(a)).orElseThrow();
        running.transitionTo(SpecQueueItemStatus.RUNNING, Instant.now());
        running.recordRun("run-1", Instant.now());
        items.saveAndFlush(running);
        doThrow(new IllegalStateException("down")).when(embabel).stopAgentRun("run-1");

        mvc.perform(delete(base() + "/items/" + a).with(user()))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMBABEL_UNAVAILABLE"));
        SpecQueueItem after = items.findById(UUID.fromString(a)).orElseThrow();
        assertThat(after.getStatus()).isEqualTo(SpecQueueItemStatus.RUNNING);
        assertThat(after.getCancelRequestedAt()).isNull();
    }

    @Test
    void secondActiveItemIsRejectedByTheDatabase() {
        UUID aId = UUID.fromString(enqueueUnchecked("a.md"));
        UUID bId = UUID.fromString(enqueueUnchecked("b.md"));
        SpecQueueItem a = items.findById(aId).orElseThrow();
        a.transitionTo(SpecQueueItemStatus.RUNNING, Instant.now());
        items.saveAndFlush(a);
        SpecQueueItem b = items.findById(bId).orElseThrow();
        b.transitionTo(SpecQueueItemStatus.RUNNING, Instant.now());
        org.junit.jupiter.api.Assertions.assertThrows(
                org.springframework.dao.DataIntegrityViolationException.class, () -> items.saveAndFlush(b));
    }

    private String enqueueUnchecked(String file) {
        try {
            return enqueue(file);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void manualWorkflowStartIsBlockedOnlyWhileAnItemIsActive() throws Exception {
        String a = enqueue("a.md");
        String body = "{\"projectId\":\"" + projectId + "\"}";
        when(embabel.startWorkflow(any(String.class), any())).thenReturn(null);

        mvc.perform(post("/api/workflows/wf-1/start").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        SpecQueueItem active = items.findById(UUID.fromString(a)).orElseThrow();
        active.transitionTo(SpecQueueItemStatus.RUNNING, Instant.now());
        items.saveAndFlush(active);
        mvc.perform(post("/api/workflows/wf-1/start").with(user()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPEC_QUEUE_ITEM_ACTIVE"));
        mvc.perform(post("/api/workflows/wf-1/start").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"garbage\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/agentic-workflows/start").with(user()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"specFileRef\":\"a.md\",\"projectId\":\"" + projectId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors[0].code").value("SPEC_QUEUE_ITEM_ACTIVE"));
    }
}
