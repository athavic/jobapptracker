package com.jobtracker.application;

import com.jobtracker.application.dto.ApplicationResponse;
import com.jobtracker.application.dto.ChangeStatusRequest;
import com.jobtracker.application.dto.CreateApplicationRequest;
import com.jobtracker.application.dto.PageResponse;
import com.jobtracker.application.dto.UpdateApplicationRequest;
import com.jobtracker.common.NotFoundException;
import com.jobtracker.common.WorkspaceContext;
import com.jobtracker.company.Company;
import com.jobtracker.company.CompanyRepository;
import com.jobtracker.tenancy.Workspace;
import com.jobtracker.tenancy.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * One workspace must not be able to read, change or delete another's rows.
 *
 * <p>Against a real PostgreSQL, because the claim is about SQL. Scoping lives in
 * a Specification that becomes a WHERE clause; a mocked repository would happily
 * return whatever the test told it to and prove nothing at all. Started and
 * discarded per run by Testcontainers, so it never touches your dev database.
 *
 * <p>The fixture is two workspaces with one application each. Every test asks
 * for the OTHER workspace's row and expects to be told it does not exist -
 * <b>404, not 403</b>. A 403 confirms the row is real, which turns the id space
 * into an oracle: you cannot read the application, but you can learn exactly
 * which ids exist and how many there are.
 *
 * <p>Written before the fix and watched to fail, which is the only way to know a
 * leak test can detect the leak.
 */
@SpringBootTest
@Testcontainers
class WorkspaceScopingTest {

    /**
     * Same major version as docker-compose.yml and CI. A scoping bug that only
     * appears on the real engine is exactly the kind this test exists to catch,
     * so the engine has to be the real one.
     */
    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    @DynamicPropertySource
    static void credentials(DynamicPropertyRegistry registry) {
        // The app refuses to start without a client registration. Nothing here
        // signs in, so placeholders are enough - they are never sent to Google.
        registry.add("spring.security.oauth2.client.registration.google.client-id",
                () -> "scoping-test-client-id");
        registry.add("spring.security.oauth2.client.registration.google.client-secret",
                () -> "scoping-test-client-secret");

        // From 5e, migrations run as the schema owner rather than as the
        // application's role. In production that owner is DB_USER; in a
        // Testcontainers database it is whatever the container created, so the
        // defaults in application.yml would authenticate as a role this
        // PostgreSQL has never heard of.
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
    }

    /**
     * The seam under test. Replacing it is how one test acts as a member of
     * workspace A and the next as a member of workspace B, without simulating a
     * whole OAuth sign-in to change one number.
     */
    @MockitoBean
    private WorkspaceContext workspaceContext;

    @Autowired
    private ApplicationService service;
    @Autowired
    private WorkspaceRepository workspaces;
    @Autowired
    private CompanyRepository companies;
    @Autowired
    private JobApplicationRepository applications;

    private Long workspaceA;
    private Long workspaceB;
    private Long applicationInA;
    private Long applicationInB;

    @BeforeEach
    void twoWorkspaces() {
        applications.deleteAll();
        companies.deleteAll();

        workspaceA = workspaces.save(new Workspace("A")).getId();
        workspaceB = workspaces.save(new Workspace("B")).getId();

        applicationInA = anApplication(workspaceA, "Stripe", "Backend Engineer");
        applicationInB = anApplication(workspaceB, "Figma", "Design Engineer");
    }

    private Long anApplication(Long workspaceId, String companyName, String role) {
        Company company = companies.save(new Company(workspaceId, companyName));
        return applications
                .save(new JobApplication(company, role, ApplicationStatus.APPLIED))
                .getId();
    }

    private void actingIn(Long workspaceId) {
        given(workspaceContext.currentId()).willReturn(workspaceId);
        given(workspaceContext.readScope()).willReturn(Optional.of(workspaceId));
    }

    // ------------------------------------------------------------------ reads

    @Test
    @DisplayName("the list shows only the current workspace")
    void listIsScoped() {
        actingIn(workspaceA);

        PageResponse<ApplicationResponse> page =
                service.search(List.of(), null, false, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(ApplicationResponse::id)
                .containsExactly(applicationInA);
    }

    @Test
    @DisplayName("fetching another workspace's application is a 404, not a 403")
    void getIsScoped() {
        actingIn(workspaceA);

        // NotFoundException rather than an access-denied exception, deliberately.
        // 403 would confirm the row exists, and an attacker who can distinguish
        // "no such id" from "not yours" can enumerate every application in the
        // system without reading one.
        assertThatThrownBy(() -> service.get(applicationInB))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("another workspace's timeline is a 404")
    void eventsAreScoped() {
        actingIn(workspaceA);

        assertThatThrownBy(() -> service.events(applicationInB))
                .isInstanceOf(NotFoundException.class);
    }

    // ----------------------------------------------------------------- writes

    @Test
    @DisplayName("another workspace's application cannot be edited")
    void updateIsScoped() {
        actingIn(workspaceA);

        assertThatThrownBy(() -> service.update(applicationInB, anUpdate()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("another workspace's application cannot be moved through the lifecycle")
    void changeStatusIsScoped() {
        actingIn(workspaceA);

        assertThatThrownBy(() -> service.changeStatus(
                applicationInB, new ChangeStatusRequest(ApplicationStatus.REJECTED, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("another workspace's application cannot be archived")
    void archiveIsScoped() {
        actingIn(workspaceA);

        assertThatThrownBy(() -> service.archive(applicationInB))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("another workspace's application cannot be deleted, and survives the attempt")
    void deleteIsScoped() {
        actingIn(workspaceA);

        assertThatThrownBy(() -> service.delete(applicationInB))
                .isInstanceOf(NotFoundException.class);

        // The assertion that matters. A delete that refuses loudly but removes
        // the row anyway would pass every test above this line.
        assertThat(applications.findById(applicationInB)).isPresent();
    }

    // ---------------------------------------------------------------- the edges

    @Test
    @DisplayName("a new application lands in the workspace that created it")
    void createStaysInItsWorkspace() {
        actingIn(workspaceB);

        ApplicationResponse created = service.create(new CreateApplicationRequest(
                "Linear", "Product Engineer", ApplicationStatus.SAVED,
                null, null, null, null, null, null, null, null, null, null, null, null));

        actingIn(workspaceA);
        assertThatThrownBy(() -> service.get(created.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("the same company name in two workspaces stays two separate companies")
    void companiesDoNotLeakAcrossWorkspaces() {
        actingIn(workspaceA);
        ApplicationResponse inA = service.create(applicationAt("Notion"));

        actingIn(workspaceB);
        ApplicationResponse inB = service.create(applicationAt("Notion"));

        // Same name, two rows. uq_company_workspace_name permits this on
        // purpose: two workspaces both tracking Notion is normal, and sharing
        // the row would let one workspace rename the other's company.
        assertThat(inA.company().id()).isNotEqualTo(inB.company().id());
    }

    @Test
    @DisplayName("the worker reads across every workspace, because that is its job")
    void serviceScopeSeesEverything() {
        // The Python worker has no workspace. nudge_stale scans all of them, so
        // an empty scope means "no filter" - the one caller for which that is
        // correct, and the reason readScope returns an Optional rather than a
        // Long that could be quietly defaulted.
        given(workspaceContext.readScope()).willReturn(Optional.empty());

        PageResponse<ApplicationResponse> page =
                service.search(List.of(), null, false, PageRequest.of(0, 20));

        assertThat(page.content()).extracting(ApplicationResponse::id)
                .containsExactlyInAnyOrder(applicationInA, applicationInB);
    }

    private CreateApplicationRequest applicationAt(String companyName) {
        return new CreateApplicationRequest(
                companyName, "Engineer", ApplicationStatus.SAVED,
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static UpdateApplicationRequest anUpdate() {
        return new UpdateApplicationRequest(
                null, "Renamed By Another Workspace", null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }
}
