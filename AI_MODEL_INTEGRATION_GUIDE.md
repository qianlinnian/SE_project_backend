# AI模型接入指导手册

## 🎯 您的AI模型接入步骤

### 1. AI模型部署方式
```java
// 在 VideoDetectionService.java 中添加您的AI模型调用
@Service
public class VideoDetectionService {
    
    // 如果您的AI模型是HTTP服务
    @Autowired
    private RestTemplate restTemplate;
    
    // 如果您的AI模型是本地模型库
    private YourAIModel aiModel;
    
    /**
     * 调用您的AI模型进行检测
     */
    public Map<String, Object> callYourAIModel(String imageUrl) {
        // 方式1：HTTP调用
        Map<String, Object> request = Map.of("image_url", imageUrl);
        return restTemplate.postForObject("http://your-ai-service/detect", request, Map.class);
        
        // 方式2：本地模型调用
        // return aiModel.detect(imageUrl);
    }
}
```

### 2. 三种接入模式详解

#### 模式一：直接结果上报（推荐）
**适用场景：** 您的AI模型已经处理完图片/视频，直接上报结果

```http
POST http://localhost:8081/api/violation-detection/detect-frame
Content-Type: application/json

{
  "intersectionId": 1,
  "plateNumber": "沪A12345",
  "violationType": "闯红灯",        // 闯红灯/逆行/跨实线/违法转弯
  "imageUrl": "http://storage/evidence.jpg",
  "confidence": 0.95,
  "cameraId": "cam-001",
  "description": "AI检测结果"
}
```

#### 模式二：图片上传分析
**适用场景：** 上传图片，由系统调用您的AI模型

```http
POST http://localhost:8081/api/violation-detection/upload-image
Content-Type: multipart/form-data

Form Data:
- imageFile: [图片文件]
- intersectionId: 1
- plateNumber: 沪A12345
- violationType: 闯红灯         // 可选，如果AI模型检测
- aiConfidence: 0.95
```

需要在 `VideoDetectionController.java` 的 `uploadImageAndDetect` 方法中集成：

```java
@PostMapping(value = "/upload-image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
public Map<String, Object> uploadImageAndDetect(...) {
    // 上传图片到MinIO
    String imageUrl = videoDetectionService.uploadViolationImage(imageFile, intersectionId);
    
    // 🔥 在这里调用您的AI模型
    Map<String, Object> aiResult = videoDetectionService.callYourAIModel(imageUrl);
    
    // 使用AI检测结果构建违章数据
    Map<String, Object> detectionData = Map.of(
        "intersectionId", intersectionId,
        "plateNumber", aiResult.get("plateNumber"),  // AI检测的车牌
        "violationType", aiResult.get("violationType"), // AI检测的违章类型
        "imageUrl", imageUrl,
        "confidence", aiResult.get("confidence")
    );
}
```

#### 模式三：视频上传自动分析
**适用场景：** 上传视频，自动提取帧并调用AI模型

```http
POST http://localhost:8081/api/violation-detection/upload-video
Content-Type: multipart/form-data

Form Data:
- videoFile: [视频文件]
- intersectionId: 1
- autoAnalyze: true
```

## 🔧 违章类型映射

```java
// 在 mapViolationType 方法中添加您的映射
private String mapViolationType(String detectedType) {
    switch (detectedType.toLowerCase()) {
        case "闯红灯":
        case "red_light":
            return "RED_LIGHT";
        case "逆行":
        case "wrong_way":
            return "WRONG_WAY";
        case "变道":
        case "跨实线":
        case "cross_line":
            return "CROSS_SOLID_LINE";
        case "违法转弯":
        case "illegal_turn":
            return "ILLEGAL_TURN";
        default:
            throw new IllegalArgumentException("未知违章类型: " + detectedType);
    }
}
```

## 🚦 红绿灯时序验证（增强版）

系统支持精细化的红绿灯状态验证，区分直行绿灯和转弯绿灯：

### 红绿灯状态类型（完整版）
- `RED` - 红灯（向后兼容）
- `YELLOW` - 黄灯（向后兼容）
- `GREEN` - 绿灯（向后兼容）
- `STRAIGHT_RED` - 直行红灯
- `STRAIGHT_YELLOW` - 直行黄灯
- `STRAIGHT_GREEN` - 直行绿灯
- `TURN_RED` - 转弯红灯
- `TURN_YELLOW` - 转弯黄灯
- `TURN_GREEN` - 转弯绿灯

### 违章验证逻辑
```java
// 增强的红绿灯验证逻辑
private boolean validateViolationWithTrafficLight(String violationType, Long intersectionId, LocalDateTime time) {
    var lightState = trafficLightStateService.getCurrentLightState(intersectionId, time);
    
    switch (violationType) {
        case "RED_LIGHT":
            return lightState == LightState.RED;  // 只有红灯时才构成闯红灯
            
        case "ILLEGAL_TURN":
            // 只有转弯绿灯时转弯才合法，其他时间都构成违章
            return lightState != LightState.TURN_GREEN;
            
        case "WRONG_WAY":
        case "CROSS_SOLID_LINE":
            return true;  // 这些违法行为不受红绿灯限制
    }
}
```

### 信号灯周期设置（完整版）
系统支持独立的直行和转弯信号灯配置：

**直行信号灯：**
- `straightRedDuration` - 直行红灯时长
- `straightYellowDuration` - 直行黄灯时长
- `straightGreenDuration` - 直行绿灯时长

**转弯信号灯：**
- `turnRedDuration` - 转弯红灯时长
- `turnYellowDuration` - 转弯黄灯时长  
- `turnGreenDuration` - 转弯绿灯时长

**独立周期控制：** 直行和转弯信号灯可以有不同的周期时长，实现精确的交通流量控制

## 📊 前端展示接口

违章记录查询和管理接口已完备：

```http
# 查询违章列表（分页）
GET http://localhost:8081/api/violations?page=1&size=10

# 查看违章详情
GET http://localhost:8081/api/violations/{id}

# 处理违章（执法人员使用）
PUT http://localhost:8081/api/violations/{id}/process
{
  "status": "processed",
  "processorId": "admin-001",
  "fine": 500,
  "description": "已处罚"
}

# 获取违章总数
GET http://localhost:8081/api/violations/count

# 查询视频任务状态
GET http://localhost:8081/api/violation-detection/task-status/{taskId}
```

## 🚀 接入建议

### 推荐接入流程：

1. **第一阶段：** 使用"直接结果上报"模式快速集成
2. **第二阶段：** 根据需要增加图片上传模式
3. **第三阶段：** 完善视频自动分析功能

### AI模型接入要点：

1. **返回格式标准化：** 确保AI模型返回包含 `plateNumber`、`violationType`、`confidence`
2. **违章类型映射：** 将您的模型输出映射到系统标准类型
3. **置信度阈值：** 设定合适的置信度阈值过滤误检
4. **异常处理：** 处理AI模型调用失败的情况

### 部署建议：

- 如果AI模型独立部署为HTTP服务，使用 `RestTemplate` 调用
- 如果AI模型集成到后端，直接在 `VideoDetectionService` 中调用
- 考虑异步处理大批量视频分析任务

## 🧪 测试验证

使用提供的测试文件验证功能：
- `violation-detection-tests.http` - 违章检测功能测试
- `api-tests.http` - 完整API测试套件

系统已完全准备好接入您的AI模型！🎉