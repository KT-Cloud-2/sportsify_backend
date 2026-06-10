package com.sportsify.ticketing.presentation;

import com.sportsify.support.WebMvcTestSupport;
import com.sportsify.ticketing.application.service.OrderService;
import com.sportsify.ticketing.presentation.controller.OrderAdminController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderAdminController.class)
class OrderAdminControllerApiTest extends WebMvcTestSupport {
    private static final Long TEST_MEMBER_ID = 1L;
    @MockitoBean
    private OrderService orderService;


    @Test
    @DisplayName("POST /api/admin/orders/complete-sync — 200 미완료 주문건 재처리 성공")
    void admin_completeStuckOrders_success() throws Exception {
        when(orderService.completeStuckOrders()).thenReturn(1);

        mockMvc.perform(post("/api/admin/orders/complete-sync")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string("수동 처리가 완료되었습니다. (성공: 1건)"));

    }

    @Test
    @DisplayName("POST /api/admin/orders/complete-sync — 403 admin이 아닌 일반 유저는 실패")
    public void notAllowNormalUser_fails() throws Exception {
        mockMvc.perform(post("/api/admin/orders/complete-sync")
                        .header("Authorization", bearerToken(TEST_MEMBER_ID, "USER")))
                .andExpect(status().isForbidden());
    }
}
