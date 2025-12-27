package com.traffic.management.dto.traffic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * 路口详细数据 DTO
 * 独立文件
 */
@Data
@NoArgsConstructor
public class IntersectionDTO {
    private Integer id;
    
    @JsonProperty("signal_phase")
    private String signalPhase;
    
    @JsonProperty("phase_code")
    private Integer phaseCode;
    
    @JsonProperty("queue_length")
    private Integer queueLength;
    
    @JsonProperty("vehicle_count")
    private Integer vehicleCount;
    
    // Key 为车道名 (如 "NT", "NL"), Value 为车道详情
    private Map<String, LaneDTO> lanes;
}