package com.traffic.management.entity;

/**
 * 车辆类型枚举
 * 用于记录违章车辆的类型
 */
public enum VehicleType {

    CAR("car", "小汽车"),
    BUS("bus", "公交车"),
    TRUCK("truck", "卡车"),
    OTHER("other", "其他");

    private final String code;
    private final String description;

    VehicleType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据代码获取枚举值
     */
    public static VehicleType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return OTHER;
        }
        for (VehicleType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return OTHER;
    }

    /**
     * 获取中文名称
     */
    public static String getChineseName(String code) {
        return fromCode(code).getDescription();
    }
}
