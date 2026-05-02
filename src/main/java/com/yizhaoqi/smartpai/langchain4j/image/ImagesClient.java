package com.yizhaoqi.smartpai.langchain4j.image;

import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.V;

/**
 * @author caiyuping
 * @date 2026/5/9 9:33
 * @description: 这个是一个识图模型
 */
public interface ImagesClient {
    @SystemMessage("请详细描述这张图片中的所有内容，包括：\n" +
            "1. 图片中的所有文字（逐字提取，保留原文）\n" +
            "2. 表格数据（用Markdown表格格式输出）\n" +
            "3. 图表的关键信息（标题、坐标轴、数据趋势）\n" +
            "4. 图片中的关键元素和布局关系\n" +
            "\n" +
            "注意：只输出图片中明确存在的内容，不要添加任何推断或补充。")
    String imagesModel(UserMessage userMessage);
}
