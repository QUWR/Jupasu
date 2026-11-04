package com.a505.jupasu.domain.wine;

import com.a505.jupasu.domain.user.entity.User;
import com.a505.jupasu.global.security.jwt.JwtTokenProvider;
import com.a505.jupasu.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * JWT 필터를 포함한 실제 요청 흐름으로 API 응답을 검증한다.
 */
class WineApiIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;
    private String bearer;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        User user = createUser();
        bearer = "Bearer " + jwtTokenProvider.createAccessToken(user.getEmail());
    }

    private JsonNode getJson(String url) throws Exception {
        MvcResult result = mockMvc.perform(get(url).header("Authorization", bearer)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsByteArray());
    }

    @Test
    @DisplayName("키워드 검색의 '기본순'은 이름 유사도가 높은 순서로 나온다")
    void keywordSearch_defaultSort_isOrderedBySimilarity() throws Exception {
        // 결과마다 유사도 값이 달라 정렬 여부가 드러나는 검색어 (실데이터 기준)
        String keyword = "그란 레세르바";
        JsonNode content = getJson("/api/wines?keyword=" + keyword + "&page=0&size=10").path("data").path("content");
        assertThat(content.size()).isGreaterThan(1);

        List<Double> similarities = new ArrayList<>();
        for (JsonNode wine : content) {
            similarities.add(jdbcTemplate.queryForObject(
                    "SELECT word_similarity(?, name_kr) FROM wine WHERE id = ?", Double.class, keyword, wine.path("id").asLong()));
        }
        for (int i = 1; i < similarities.size(); i++) {
            assertThat(similarities.get(i)).as("유사도 순서 " + similarities).isLessThanOrEqualTo(similarities.get(i - 1));
        }
    }

    @Test
    @DisplayName("가격 낮은순은 0원을 제외하고, 같은 가격이 많아도 페이지 사이에 중복·누락이 없다")
    void priceAscending_excludesZeroAndPagesAreStable() throws Exception {
        Set<Long> seen = new HashSet<>();
        long previousPrice = -1;
        for (int page = 0; page < 5; page++) {
            JsonNode content = getJson("/api/wines?types=RED&sort=price,asc&size=20&page=" + page).path("data").path("content");
            for (JsonNode wine : content) {
                long price = wine.path("price").asLong();
                assertThat(price).isGreaterThan(0);
                assertThat(price).isGreaterThanOrEqualTo(previousPrice);
                assertThat(seen.add(wine.path("id").asLong())).as("페이지 간 중복 id " + wine.path("id")).isTrue();
                previousPrice = price;
            }
        }
    }

    @Test
    @DisplayName("와인 상세 조회 1회에 실행되는 SQL 수 (사용자·취향 중복 조회 제거 확인)")
    void wineDetail_queryCount() throws Exception {
        long wineId = jdbcTemplate.queryForObject(
                "SELECT wine_id FROM wine_food_pairing ORDER BY wine_id LIMIT 1", Long.class);
        getJson("/api/wines/" + wineId);   // 워밍업 (최초 호출 시 메타데이터 로딩 제외)

        org.hibernate.stat.Statistics stats = statistics();
        stats.clear();
        JsonNode detail = getJson("/api/wines/" + wineId).path("data");
        long statements = stats.getPrepareStatementCount();
        System.out.println("[상세 조회] 실행된 SQL 수(JWT 필터의 사용자 조회 포함) = " + statements);

        assertThat(detail.path("wineId").asLong()).isEqualTo(wineId);
        assertThat(detail.path("pairingFoods").size()).isGreaterThan(0);
        assertThat(statements).isLessThanOrEqualTo(MAX_DETAIL_STATEMENTS);
    }

    /** 수정 전 실측 10건에서 중복 조회 2건(사용자 findById, 적중률 계산 안의 취향 재조회)을 뺀 값 */
    private static final long MAX_DETAIL_STATEMENTS = 8;

    @Test
    @DisplayName("존재하지 않는 경로는 500이 아니라 404")
    void unknownPath_returns404() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/wines-no-such-path").header("Authorization", bearer)).andReturn();
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("없는 와인에 리뷰를 쓰면 HTTP 404, 같은 와인에 두 번 쓰면 HTTP 409")
    void reviewApi_returnsProperStatusCodes() throws Exception {
        String body = "{\"rating\": 5, \"content\": \"좋아요\"}";

        MvcResult missing = mockMvc.perform(post("/api/reviews/8999999999/review")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);

        long wineId = insertTestWine("HTTP상태코드와인", 30000);
        MvcResult first = mockMvc.perform(post("/api/reviews/" + wineId + "/review")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(first.getResponse().getStatus()).isEqualTo(200);

        MvcResult second = mockMvc.perform(post("/api/reviews/" + wineId + "/review")
                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
        assertThat(second.getResponse().getStatus()).isEqualTo(409);
    }
}
