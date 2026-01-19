package com.yizhaoqi.smartpai.langchain4j;


import com.yizhaoqi.smartpai.langchain4j.chat.ChatUtils;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class Test01 {

    @Resource
    ChatUtils chatUtils;


    @Test
    public void test1() throws InterruptedException {
        String UserMessage = "你好";

        chatUtils.Test(UserMessage,"我是小明",null,null,null);
    }
}
