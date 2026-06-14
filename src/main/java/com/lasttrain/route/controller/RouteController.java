package com.lasttrain.route.controller;

import com.lasttrain.global.response.ApiResponse;
import com.lasttrain.route.dto.RouteResponse;
import com.lasttrain.route.service.RouteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Route", description = "막차 경로 조회")
@RequestMapping("/api/v1")
@RestController
@RequiredArgsConstructor
public class RouteController {

    private final RouteService routeService;

    @SecurityRequirements({})
    @Operation(
            summary = "막차 경로 조회",
            description = """
                    출발지/목적지 좌표로 막차 경로와 탑승 마감 시각을 조회합니다.

                    - **비회원 허용**: 로그인 없이 사용 가능
                    - **즐겨찾기 연동**: favoriteId 전달 시 해당 목적지 좌표로 조회 (로그인 필요)
                    - **응답**: departureDeadline 내림차순 정렬 (늦은 막차 우선)
                    - **주의**: busLastTime은 정적 시간표 기반. 실제 결행/지연 미반영.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "경로 없음"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "ODsay API 오류")
    })
    @GetMapping("/last-train")
    public ApiResponse<RouteResponse> getLastTrain(
            @Parameter(description = "출발지 위도", example = "37.5172", required = true)
            @RequestParam double originLat,

            @Parameter(description = "출발지 경도", example = "127.0473", required = true)
            @RequestParam double originLng,

            @Parameter(description = "출발지 명칭", example = "강남구청", required = true)
            @RequestParam String originName,

            @Parameter(description = "목적지 위도", example = "37.5034", required = true)
            @RequestParam double destLat,

            @Parameter(description = "목적지 경도", example = "126.7660", required = true)
            @RequestParam double destLng,

            @Parameter(description = "목적지 명칭", example = "부천역", required = true)
            @RequestParam String destName,

            @Parameter(description = "즐겨찾기 ID (선택, 로그인 필요)", example = "1")
            @RequestParam(required = false) Long favoriteId
    ) {
        // TODO: favoriteId가 전달되면 즐겨찾기에 저장된 목적지 좌표/명칭으로 덮어쓰는 로직 구현 (추후 구현)

        RouteResponse response = routeService.findLastTrainRoutes(
                originLat, originLng, originName, destLat, destLng, destName);

        return ApiResponse.ok(response);
    }
}