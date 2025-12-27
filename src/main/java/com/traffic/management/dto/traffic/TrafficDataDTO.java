package com.traffic.management.dto.traffic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 对应 LLM 推送的完整交通数据结构
 * 引用独立的 IntersectionDTO
 */
@Data
@NoArgsConstructor
public class TrafficDataDTO {
    private Double timestamp;
    private Integer step;
    private String roadnet;
    private String trafficflow;
    
    @JsonProperty("control_mode")
    private String controlMode;
    
    @JsonProperty("total_intersections")
    private Integer totalIntersections;
    
    // 聚合数据（前端展示用，可选）
    @JsonProperty("total_queue")
    private Integer totalQueue;
    
    @JsonProperty("total_vehicles")
    private Integer totalVehicles;

    private List<IntersectionDTO> intersections;
}