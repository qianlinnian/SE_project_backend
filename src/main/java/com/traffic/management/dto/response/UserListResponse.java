package com.traffic.management.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户列表响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListResponse {

    /**
     * 用户列表
     */
    private List<UserDTO> users;

    /**
     * 总记录数
     */
    private Long total;
}
