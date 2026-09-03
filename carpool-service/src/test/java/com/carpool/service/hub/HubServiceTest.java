package com.carpool.service.hub;

import com.carpool.common.exception.HubNotFoundException;
import com.carpool.domain.entity.Hub;
import com.carpool.domain.entity.User;
import com.carpool.domain.enums.HubStatus;
import com.carpool.domain.enums.UserRole;
import com.carpool.domain.enums.UserStatus;
import com.carpool.repository.HubRepository;
import com.carpool.repository.UserRepository;
import com.carpool.service.dto.request.SuggestHubRequest;
import com.carpool.service.dto.response.HubResponse;
import com.carpool.service.mapper.EntityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("HubService")
class HubServiceTest {

    @Mock private HubRepository  hubRepository;
    @Mock private UserRepository userRepository;
    @Mock private EntityMapper   mapper;

    @InjectMocks
    private HubService hubService;

    private User driver;
    private Hub  activeHub;

    @BeforeEach
    void setUp() {
        driver = User.builder()
                .id(1L).telegramId(111L).fullName("Driver Juan")
                .role(UserRole.DRIVER).status(UserStatus.ACTIVE)
                .build();

        activeHub = Hub.builder()
                .id(1L).code("BGC_HIGH_STREET")
                .name("BGC High Street").area("Taguig")
                .status(HubStatus.ACTIVE).build();
    }

    private static void stubMapperPassthrough(EntityMapper mapper) {
        when(mapper.toHubResponse(any())).thenAnswer(inv -> {
            Hub h = inv.getArgument(0);
            return new HubResponse(h.getId(), h.getCode(), h.getName(), h.getArea(), h.getStatus());
        });
    }

    @Test
    @DisplayName("getAllActiveHubs() should return only ACTIVE hubs")
    void shouldReturnActiveHubs() {
        when(hubRepository.findByStatusOrderByAreaAscNameAsc(HubStatus.ACTIVE))
                .thenReturn(List.of(activeHub));
        when(mapper.toHubResponse(activeHub))
                .thenReturn(new HubResponse(1L, "BGC_HIGH_STREET",
                        "BGC High Street", "Taguig", HubStatus.ACTIVE));

        List<HubResponse> result = hubService.getAllActiveHubs();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).code()).isEqualTo("BGC_HIGH_STREET");
    }

    @Test
    @DisplayName("suggestHub() should save hub as PENDING when name is new")
    void shouldSavePendingHubOnSuggest() {
        var request = new SuggestHubRequest("Wilcon Depot Sucat", "Parañaque");

        when(hubRepository.findFirstByNameIgnoreCaseAndArea("Wilcon Depot Sucat", "Parañaque"))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(driver));
        when(hubRepository.save(any(Hub.class))).thenAnswer(inv -> {
            Hub h = inv.getArgument(0);
            h.setId(99L);
            return h;
        });
        when(hubRepository.countByStatus(HubStatus.PENDING)).thenReturn(1L);
        stubMapperPassthrough(mapper);

        HubResponse result = hubService.suggestHub(request, 1L);

        ArgumentCaptor<Hub> captor = ArgumentCaptor.forClass(Hub.class);
        verify(hubRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HubStatus.PENDING);
        assertThat(captor.getValue().getName()).isEqualTo("Wilcon Depot Sucat");
        assertThat(result.status()).isEqualTo(HubStatus.PENDING);
        verify(hubRepository, never()).findAllPendingForUpdate();
    }

    @Test
    @DisplayName("suggestHub() should return the existing hub as-is when it's already ACTIVE")
    void shouldDedupeActiveHub() {
        var request = new SuggestHubRequest("BGC High Street", "Taguig");
        when(hubRepository.findFirstByNameIgnoreCaseAndArea("BGC High Street", "Taguig"))
                .thenReturn(Optional.of(activeHub));
        when(mapper.toHubResponse(activeHub)).thenReturn(
                new HubResponse(1L, "BGC_HIGH_STREET", "BGC High Street", "Taguig", HubStatus.ACTIVE));

        HubResponse result = hubService.suggestHub(request, 1L);

        assertThat(result.status()).isEqualTo(HubStatus.ACTIVE);
        verify(hubRepository, never()).save(any());
        verify(hubRepository, never()).countByStatus(any());
    }

    @Test
    @DisplayName("suggestHub() should re-queue a REJECTED hub back to PENDING")
    void shouldRequeueRejectedHub() {
        Hub rejectedHub = Hub.builder()
                .id(5L).name("Old Spot").area("Nowhere").status(HubStatus.REJECTED).build();
        var request = new SuggestHubRequest("Old Spot", "Nowhere");

        when(hubRepository.findFirstByNameIgnoreCaseAndArea("Old Spot", "Nowhere"))
                .thenReturn(Optional.of(rejectedHub));
        when(hubRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hubRepository.countByStatus(HubStatus.PENDING)).thenReturn(1L);
        stubMapperPassthrough(mapper);

        HubResponse result = hubService.suggestHub(request, 1L);

        assertThat(result.status()).isEqualTo(HubStatus.PENDING);
    }

    @Test
    @DisplayName("suggestHub() should NOT auto-approve while the pending queue is below threshold")
    void shouldNotAutoApproveBelowThreshold() {
        var request = new SuggestHubRequest("Wilcon Depot Sucat", "Parañaque");

        when(hubRepository.findFirstByNameIgnoreCaseAndArea("Wilcon Depot Sucat", "Parañaque"))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(driver));
        when(hubRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hubRepository.countByStatus(HubStatus.PENDING)).thenReturn(9L);
        stubMapperPassthrough(mapper);

        HubResponse result = hubService.suggestHub(request, 1L);

        assertThat(result.status()).isEqualTo(HubStatus.PENDING);
        verify(hubRepository, never()).findAllPendingForUpdate();
    }

    @Test
    @DisplayName("suggestHub() should auto-approve every pending hub once the queue reaches 10")
    void shouldAutoApproveAllPendingHubsWhenQueueReachesThreshold() {
        var request = new SuggestHubRequest("Wilcon Depot Sucat", "Parañaque");

        when(hubRepository.findFirstByNameIgnoreCaseAndArea("Wilcon Depot Sucat", "Parañaque"))
                .thenReturn(Optional.empty());
        when(userRepository.findById(1L)).thenReturn(Optional.of(driver));

        Hub[] created = new Hub[1];
        when(hubRepository.save(any(Hub.class))).thenAnswer(inv -> {
            Hub h = inv.getArgument(0);
            if (h.getId() == null) {
                h.setId(99L);
                created[0] = h;
            }
            return h;
        });
        when(hubRepository.countByStatus(HubStatus.PENDING)).thenReturn(10L);
        when(hubRepository.findAllPendingForUpdate()).thenAnswer(inv -> {
            List<Hub> otherPending = new ArrayList<>();
            for (long i = 1; i <= 9; i++) {
                otherPending.add(Hub.builder().id(i).name("Other " + i).area("Somewhere")
                        .status(HubStatus.PENDING).build());
            }
            otherPending.add(created[0]);
            return otherPending;
        });
        when(hubRepository.findByCode(any())).thenReturn(Optional.empty());
        stubMapperPassthrough(mapper);

        HubResponse result = hubService.suggestHub(request, 1L);

        assertThat(result.status()).isEqualTo(HubStatus.ACTIVE);
        assertThat(result.code()).isEqualTo("WILCON_DEPOT_SUCAT");
        verify(hubRepository).findAllPendingForUpdate();
        // Exactly 10 pending hubs (9 pre-existing + the newly created one), one generateUniqueCode call each
        verify(hubRepository, times(10)).findByCode(any());
    }

    @Test
    @DisplayName("approveHub() should set status to ACTIVE and assign code")
    void shouldApproveHubAndSetCode() {
        Hub pendingHub = Hub.builder()
                .id(99L).name("Wilcon Depot Sucat")
                .area("Parañaque").status(HubStatus.PENDING).build();

        when(hubRepository.findById(99L)).thenReturn(Optional.of(pendingHub));
        when(hubRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        stubMapperPassthrough(mapper);

        HubResponse result = hubService.approveHub(99L, "WILCON_SUCAT");

        assertThat(result.status()).isEqualTo(HubStatus.ACTIVE);
        assertThat(result.code()).isEqualTo("WILCON_SUCAT");  // uppercased in service
    }

    @Test
    @DisplayName("approveHub() should throw HubNotFoundException for unknown id")
    void shouldThrowWhenHubNotFoundOnApprove() {
        when(hubRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> hubService.approveHub(999L, "SOME_CODE"))
                .isInstanceOf(HubNotFoundException.class);
    }

    @Test
    @DisplayName("rejectHub() should set status to REJECTED")
    void shouldRejectHub() {
        Hub pendingHub = Hub.builder()
                .id(99L).name("Fake Hub").area("Nowhere")
                .status(HubStatus.PENDING).build();

        when(hubRepository.findById(99L)).thenReturn(Optional.of(pendingHub));
        when(hubRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        hubService.rejectHub(99L);

        ArgumentCaptor<Hub> captor = ArgumentCaptor.forClass(Hub.class);
        verify(hubRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(HubStatus.REJECTED);
    }
}
