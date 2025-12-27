package com.traffic.management.dto.traffic;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * 车道详细数据 DTO
 * 独立文件
 */
@Data
@NoArgsConstructor
public class LaneDTO {
    // 对应 cells: [1, 2, 0, 0]
    // 分别代表 [0-10%, 10-20%, 20-33%, 33%+] 路段的车辆数
    private List<Integer> cells;
    
    @JsonProperty("queue_len")
    private Integer queueLen;
}