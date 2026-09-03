package com.jobtracker.tenancy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * The decisions sign-in makes, with the repositories mocked.
 *
 * <p>Worth testing directly because two of them are only ever exercised once
 * per database and are invisible when they go wrong: which workspace a brand
 * new person lands in, and whether the pre-user data gets adopted or stranded.
 * A bug here does not throw - it silently puts someone in the wrong tenant.
 */
@ExtendWith(MockitoExtension.class)
class SignInServiceTest {

    private static final Long BOOTSTRAP = 1L;

    @Mock
    private AppUserRepository users;
    @Mock
    private WorkspaceRepository workspaces;
    @Mock
    private WorkspaceMemberRepository members;

    private SignInService service;

    @BeforeEach
    void setUp() {
        service = new SignInService(users, workspaces, members, BOOTSTRAP);
    }

    @Test
    @DisplayName("the first person to sign in adopts the workspace the migration created")
    void firstUserAdoptsTheBootstrapWorkspace() {
        givenNoExistingUser();
        given(workspaces.findWithLockById(BOOTSTRAP)).willReturn(Optional.of(workspace(BOOTSTRAP)));
        given(members.existsByWorkspaceId(BOOTSTRAP)).willReturn(false);

        SignedInUser signedIn = service.signIn("sub-1", "ada@example.com", "Ada", null);

        // Everything that existed before there were users lives in this
        // workspace. Creating a fresh one instead would leave your own
        // applications in a workspace you are not a member of.
        assertThat(signedIn.workspaceId()).isEqualTo(BOOTSTRAP);
        verify(workspaces, never()).save(any());
        assertThat(savedMembership().getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("everyone after the first gets a workspace of their own")
    void laterUsersGetTheirOwnWorkspace() {
        givenNoExistingUser();
        given(workspaces.findWithLockById(BOOTSTRAP)).willReturn(Optional.of(workspace(BOOTSTRAP)));
        given(members.existsByWorkspaceId(BOOTSTRAP)).willReturn(true);
        given(workspaces.save(any())).willAnswer(call -> withId(call.getArgument(0), 42L));

        SignedInUser signedIn = service.signIn("sub-2", "grace@example.com", "Grace", null);

        // The claimed check is what stops a stranger landing inside somebody
        // else's applications on their very first sign-in.
        assertThat(signedIn.workspaceId()).isEqualTo(42L);
        assertThat(savedMembership().getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    @DisplayName("a returning user keeps the workspace they already belong to")
    void returningUserKeepsTheirWorkspace() {
        AppUser existing = withId(new AppUser("sub-1", "ada@example.com"), 5L);
        given(users.findByGoogleSub("sub-1")).willReturn(Optional.of(existing));
        given(members.findByAppUserIdOrderByIdAsc(5L))
                .willReturn(List.of(new WorkspaceMember(9L, 5L, WorkspaceRole.OWNER)));

        SignedInUser signedIn = service.signIn("sub-1", "ada@example.com", "Ada", null);

        assertThat(signedIn.workspaceId()).isEqualTo(9L);
        verify(workspaces, never()).save(any());
        verify(members, never()).save(any());
    }

    @Test
    @DisplayName("the stored profile is refreshed from the token on every sign-in")
    void profileIsRefreshedEachTime() {
        AppUser existing = withId(new AppUser("sub-1", "old@example.com"), 5L);
        given(users.findByGoogleSub("sub-1")).willReturn(Optional.of(existing));
        given(members.findByAppUserIdOrderByIdAsc(5L))
                .willReturn(List.of(new WorkspaceMember(9L, 5L, WorkspaceRole.OWNER)));

        SignedInUser signedIn = service.signIn(
                "sub-1", "new@example.com", "Ada Lovelace", "https://example.com/a.png");

        // Names, avatars and addresses change. The sub does not, which is why it
        // is the key and these are not.
        assertThat(existing.getEmail()).isEqualTo("new@example.com");
        assertThat(existing.getGoogleSub()).isEqualTo("sub-1");
        assertThat(signedIn.displayName()).isEqualTo("Ada Lovelace");
        assertThat(signedIn.avatarUrl()).isEqualTo("https://example.com/a.png");
    }

    @Test
    @DisplayName("a workspace named after the person, or their address if Google gave no name")
    void workspaceIsNamedAfterWhoeverItIsFor() {
        givenNoExistingUser();
        given(workspaces.findWithLockById(BOOTSTRAP)).willReturn(Optional.empty());
        given(workspaces.save(any())).willAnswer(call -> withId(call.getArgument(0), 42L));

        service.signIn("sub-3", "anon@example.com", null, null);

        ArgumentCaptor<Workspace> saved = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaces).save(saved.capture());
        assertThat(saved.getValue().getName()).startsWith("anon@example.com");
    }

    private void givenNoExistingUser() {
        given(users.findByGoogleSub(any())).willReturn(Optional.empty());
        given(users.save(any())).willAnswer(call -> withId(call.getArgument(0), 5L));
        given(members.findByAppUserIdOrderByIdAsc(5L)).willReturn(List.of());
    }

    private WorkspaceMember savedMembership() {
        ArgumentCaptor<WorkspaceMember> captor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(members).save(captor.capture());
        return captor.getValue();
    }

    private static Workspace workspace(Long id) {
        return withId(new Workspace("Personal"), id);
    }

    /**
     * Ids are assigned by the database, so a saved entity in a mocked repository
     * never gets one. Setting it directly is the smallest honest stand-in; the
     * alternative is a setter that exists only for tests and that production
     * code could call by accident.
     */
    private static <T> T withId(T entity, Long id) {
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
